param([string]$Directorio)
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$conexion = New-Object System.Data.OleDb.OleDbConnection "Provider=VFPOLEDB.1;Data Source=$Directorio;Collating Sequence=MACHINE;Exclusive=No;Deleted=True;"
$conexion.Open()

function Filas([string]$sql) {
    $cmd = $conexion.CreateCommand()
    $cmd.CommandText = $sql
    $tabla = New-Object System.Data.DataTable
    $adaptador = New-Object System.Data.OleDb.OleDbDataAdapter $cmd
    try { [void]$adaptador.Fill($tabla); return ,$tabla } finally { $adaptador.Dispose(); $cmd.Dispose() }
}

function Texto($valor) {
    if ($null -eq $valor -or $valor -is [DBNull]) { return '' }
    return ([string]$valor).Trim()
}

function Literal($valor) {
    if ($null -eq $valor -or $valor -is [DBNull]) { return '""' }
    if ($valor -is [bool]) { if ($valor) { return '.T.' } else { return '.F.' } }
    if ($valor -is [ValueType]) { return ([IFormattable]$valor).ToString($null,[Globalization.CultureInfo]::InvariantCulture) }
    $s = [string]$valor
    $partes = @()
    for ($i = 0; $i -lt $s.Length; $i += 150) {
        $p = $s.Substring($i,[Math]::Min(150,$s.Length-$i)).Replace('"','"+CHR(34)+"').Replace("`r",'"+CHR(13)+"').Replace("`n",'"+CHR(10)+"')
        $partes += '"'+$p+'"'
    }
    if (!$partes.Count) { return '""' }
    return '(' + ($partes -join '+') + ')'
}

function Versiones {
    return ((Get-ChildItem -LiteralPath $Directorio -File | Where-Object { $_.Extension -in '.DBF','.FPT' } | Sort-Object Name | ForEach-Object { $_.Name+':'+$_.Length+':'+$_.LastWriteTimeUtc.Ticks }) -join '|')
}

function VaciosDbf {
    $archivo = [IO.File]::Open((Join-Path $Directorio 'actual.DBF'),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
    $lector = [IO.BinaryReader]::new($archivo)
    try {
        $cabecera = $lector.ReadBytes(32)
        $cantidad = [BitConverter]::ToUInt32($cabecera,4)
        $inicio = [BitConverter]::ToUInt16($cabecera,8)
        $longitud = [BitConverter]::ToUInt16($cabecera,10)
        $campos = @()
        $posicion = 1
        while ($archivo.Position -lt $inicio) {
            if ($lector.PeekChar() -eq 13) { break }
            $c = $lector.ReadBytes(32)
            $nombre = [Text.Encoding]::ASCII.GetString($c,0,11).Trim([char]0)
            $campos += [pscustomobject]@{nombre=$nombre; tipo=[char]$c[11]; posicion=$posicion; longitud=[int]$c[16]}
            $posicion += [int]$c[16]
        }
        $resultado = @{}
        $archivo.Position = $inicio
        for ($i=0; $i -lt $cantidad; $i++) {
            $registro = $lector.ReadBytes($longitud)
            if ($registro.Length -ne $longitud) { throw 'La tabla DBF cambió durante la lectura' }
            if ($registro[0] -eq 42) { continue }
            $identidad = @{}
            $vacios = @()
            foreach ($c in $campos) {
                $texto = [Text.Encoding]::GetEncoding(1252).GetString($registro,$c.posicion,$c.longitud).Trim([char]0,[char]32)
                if ($c.nombre -in 'CODIGO','ENTIDAD','UNIDAD') { $identidad[$c.nombre] = $texto }
                if (($c.tipo -in 'N','F','L','D' -and ($texto -eq '' -or ($c.tipo -eq 'L' -and $texto -eq '?'))) -or
                    ($c.tipo -eq 'M' -and ($texto -eq '' -or $texto -match '^0+$'))) { $vacios += $c.nombre }
            }
            $resultado[$identidad.ENTIDAD+'|'+$identidad.UNIDAD+'|'+$identidad.CODIGO] = $vacios
        }
        return ,$resultado
    } finally { $lector.Dispose(); $archivo.Dispose() }
}

function Leer {
    $antes = Versiones
    $tabla = Filas @'
SELECT a.*, g.nombre AS grupo, g.depreciar, g.actualizar, x.nomaux AS auxiliar, o.nomofic AS oficina, r.nomresp AS respnom, r.cargo, e.nomestado AS estado
FROM actual a
LEFT JOIN codcont g ON g.codcont=a.codcont
LEFT JOIN auxiliar x ON x.entidad=a.entidad AND x.unidad=a.unidad AND x.codcont=a.codcont AND x.codaux=a.codaux
LEFT JOIN oficina o ON o.entidad=a.entidad AND o.unidad=a.unidad AND o.codofic=a.codofic
LEFT JOIN resp r ON r.entidad=a.entidad AND r.unidad=a.unidad AND r.codofic=a.codofic AND r.codresp=a.codresp
LEFT JOIN estado e ON e.codestado=a.codestado
WHERE ALLTRIM(a.entidad)='0143' AND ALLTRIM(a.unidad)='1'
'@
    $vacios = VaciosDbf
    $resultado = @()
    foreach ($r in $tabla.Rows) {
        $fecha = [datetime]::new([int]$r.ANO,[int]$r.MES,[int]$r.DIA).ToString('yyyy-MM-dd')
        $a = [ordered]@{
            codigo=(Texto $r.CODIGO); descripcion=(Texto $r.DESCRIP); oficinaId=[int]$r.CODOFIC
            responsableId=[int]$r.CODRESP; oficina=(Texto $r.oficina); responsable=(Texto $r.respnom); cargo=(Texto $r.cargo)
            codigoAdicional=(Texto $r.CODIGOSEC); fechaIncorporacion=$fecha; grupo=(Texto $r.grupo); auxiliar=(Texto $r.auxiliar)
            estadoActivo=(Texto $r.estado); estadoRegistro=$(if ([int]$r.API_ESTADO -eq 1) {'ELABORADO'} else {'ESTADO-'+$r.API_ESTADO})
            observaciones=(Texto $r.OBSERV); costoInicial=[decimal]$r.COSTO; depreciacionInicial=[decimal]$r.DEPACU
            vidaUtil=[decimal]$r.VIDAUTIL; depreciar=[bool]$r.depreciar; actualizar=[bool]$r.actualizar
            rube=(Texto $r.COD_RUBE); organismoFinanciador=(Texto $r.ORG_FIN); numeroConvenio=(Texto $r.NRO_CONV)
            entidad=(Texto $r.ENTIDAD); unidad=(Texto $r.UNIDAD)
        }
        $n = [ordered]@{}
        foreach ($campo in @('CODIGO','ENTIDAD','UNIDAD','CODCONT','CODAUX','VIDAUTIL','DESCRIP','COSTO','DEPACU','DIA','MES','ANO','CODOFIC','CODRESP','OBSERV','CODESTADO','COD_RUBE','NRO_CONV','ORG_FIN','API_ESTADO','CODIGOSEC')) {
            $v = $r[$campo]
            if ($v -is [string] -or $v -is [DBNull]) { $v = Texto $v }
            $n[$campo] = $v
        }
        $a['_native'] = $n
        $dbf = [ordered]@{}
        foreach ($campo in 'UNIDAD ENTIDAD CODIGO CODCONT CODAUX VIDAUTIL DESCRIP COSTO DEPACU MES ANO B_REV DIA CODOFIC CODRESP OBSERV DIA_ANT MES_ANT ANO_ANT VUT_ANT COSTO_ANT BAND_UFV CODESTADO COD_RUBE NRO_CONV ORG_FIN FEULT USUAR API_ESTADO CODIGOSEC BANDERAS FEC_MOD USU_MOD'.Split(' ')) {
            $v = $r[$campo]
            if ($v -is [DBNull]) { $v = $null }
            elseif ($v -is [datetime]) { $v = if ($v.Year -lt 1900) { $null } else { $v.ToString('yyyy-MM-dd') } }
            elseif ($v -is [string]) { $v = $v.TrimEnd() }
            $dbf[$campo] = $v
        }
        foreach ($campo in $vacios[$a.entidad+'|'+$a.unidad+'|'+$a.codigo]) { $dbf[$campo] = $null }
        $a['_dbf'] = $dbf
        $resultado += [pscustomobject]$a
    }
    if ($antes -ne (Versiones)) { throw 'FoxPro está guardando datos; se reintentará la lectura' }
    if (@($resultado.codigo | Sort-Object -Unique).Count -ne $resultado.Count) { throw 'Hay códigos duplicados en FoxPro' }
    return ,$resultado
}

function Responsables {
    $tabla = Filas @'
SELECT r.codresp, r.nomresp, r.cargo, r.codofic, o.nomofic
FROM resp r LEFT JOIN oficina o ON r.entidad=o.entidad AND r.unidad=o.unidad AND r.codofic=o.codofic
WHERE ALLTRIM(r.entidad)='0143' AND ALLTRIM(r.unidad)='1' AND !EMPTY(r.nomresp) AND !EMPTY(o.nomofic)
ORDER BY r.nomresp
'@
    $resultado = @()
    foreach ($r in $tabla.Rows) {
        $resultado += [pscustomobject]@{responsableId=[int]$r.CODRESP; responsable=(Texto $r.NOMRESP); cargo=(Texto $r.CARGO); oficinaId=[int]$r.CODOFIC; oficina=(Texto $r.NOMOFIC)}
    }
    return ,$resultado
}

function Catalogos {
    $antes = Versiones
    $oficinas = @()
    $responsables = @()
    foreach ($r in (Filas "SELECT codofic,nomofic FROM oficina WHERE ALLTRIM(entidad)='0143' AND ALLTRIM(unidad)='1' AND codofic>0 AND !EMPTY(nomofic)").Rows) {
        $oficinas += [pscustomobject]@{id=[int]$r.CODOFIC; nombre=(Texto $r.NOMOFIC)}
    }
    foreach ($r in (Filas "SELECT codresp,nomresp,cargo,codofic FROM resp WHERE ALLTRIM(entidad)='0143' AND ALLTRIM(unidad)='1' AND codresp>=0 AND !EMPTY(nomresp)").Rows) {
        $responsables += [pscustomobject]@{id=[int]$r.CODRESP; nombre=(Texto $r.NOMRESP); cargo=(Texto $r.CARGO); oficinaId=[int]$r.CODOFIC}
    }
    if (@($oficinas.id | Sort-Object -Unique).Count -ne $oficinas.Count -or @($responsables.id | Sort-Object -Unique).Count -ne $responsables.Count) {
        throw 'Hay códigos de catálogo repetidos en VSIAF; no se reemplazaron registros'
    }
    if ($antes -ne (Versiones)) { throw 'FoxPro está guardando un catálogo; se reintentará la lectura' }
    return @{oficinas=$oficinas; responsables=$responsables}
}

function GuardarCatalogo($peticion) {
    $tipo = $peticion.tipo
    if ($tipo -notin 'oficinas','responsables') { throw 'Catálogo desconocido' }
    $a = $peticion.registro
    $anterior = $peticion.anterior
    $tabla = if ($tipo -eq 'oficinas') { 'oficina' } else { 'resp' }
    $codigoCampo = if ($tipo -eq 'oficinas') { 'CODOFIC' } else { 'CODRESP' }
    $id = [int]$a.id
    $mapa = if ($tipo -eq 'oficinas') { [ordered]@{id='CODOFIC';nombre='NOMOFIC'} } else { [ordered]@{id='CODRESP';nombre='NOMRESP';cargo='CARGO';oficinaId='CODOFIC'} }
    $lineas = [Collections.Generic.List[string]]::new()
    $lineas.Add(@"
CLOSE TABLES ALL
SET EXCLUSIVE OFF
SET MULTILOCKS ON
SET DELETED ON
SET REPROCESS TO 1 SECONDS
USE $tabla SHARED IN 0 ALIAS cat
IF !FLOCK('cat') OR !MAKETRANSACTABLE('cat')
    RETURN 'ERROR: El catálogo está en edición; guarda o cancela en VSIAF'
ENDIF
BEGIN TRANSACTION
SELECT cat
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND $codigoCampo=$id
"@)
    if ($null -eq $anterior) {
        $lineas.Add("IF FOUND()`nRETURN 'ERROR: El código ya existe en VSIAF'`nENDIF`nAPPEND BLANK IN cat")
        $lineas.Add("REPLACE ENTIDAD WITH '0143',UNIDAD WITH '1' IN cat")
        $lineas.Add("IF TYPE('cat.API_ESTADO') <> 'U'`nREPLACE API_ESTADO WITH 1 IN cat`nENDIF")
    } else {
        $lineas.Add("IF !FOUND()`nRETURN 'ERROR: El catálogo fue eliminado en VSIAF'`nENDIF")
        foreach ($k in $mapa.Keys) {
            $campo = $mapa[$k]
            $valor = $anterior.$k
            $expresion = if ($valor -is [string]) { 'ALLTRIM(NVL('+ $campo +',""))' } else { $campo }
            $lineas.Add('IF !('+ $expresion +' == '+(Literal $valor)+")`nRETURN 'ERROR: El catálogo cambió en VSIAF'`nENDIF")
        }
    }
    if ($tipo -eq 'responsables') {
        $oficina = [int]$a.oficinaId
        $lineas.Add(@"
USE oficina SHARED IN 0 ALIAS ofi
IF !FLOCK('ofi')
    RETURN 'ERROR: La oficina está en edición'
ENDIF
SELECT ofi
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND CODOFIC=$oficina
IF !FOUND()
    RETURN 'ERROR: No existe la oficina del responsable en VSIAF'
ENDIF
SELECT cat
"@)
        if ($null -ne $anterior -and [int]$anterior.oficinaId -ne $oficina) {
            $lineas.Add(@"
USE actual SHARED IN 0 ALIAS af
IF !FLOCK('af')
    RETURN 'ERROR: Hay activos en edición'
ENDIF
SELECT af
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND CODRESP=$id
IF FOUND()
    RETURN 'ERROR: El responsable tiene activos asignados; conserva su oficina'
ENDIF
SELECT cat
"@)
        }
    }
    $valores = @($mapa.Keys | ForEach-Object { $mapa[$_] + ' WITH ' + (Literal $a.$_) })
    $lineas.Add('REPLACE '+($valores -join ',')+',FEULT WITH DATE(),USUAR WITH "admin" IN cat')
    $lineas.Add("END TRANSACTION`nFLUSH`nRETURN 'OK'")
    $cmd = $conexion.CreateCommand()
    $cmd.CommandText = 'EXECSCRIPT(?)'
    [void]$cmd.Parameters.Add('script',[System.Data.OleDb.OleDbType]::LongVarChar)
    $cmd.Parameters[0].Value = $lineas -join "`r`n"
    try { $resultado = $cmd.ExecuteScalar(); if ($resultado -ne 'OK') { throw $resultado } }
    finally {
        $cmd.Parameters[0].Value = "IF TXNLEVEL()>0`nROLLBACK`nENDIF`nUNLOCK ALL`nCLOSE TABLES ALL`nRETURN .T."
        try { [void]$cmd.ExecuteScalar() } finally { $cmd.Dispose() }
    }
    return 'OK'
}

function EliminarCatalogo($peticion) {
    $tipo = $peticion.tipo
    $anterior = $peticion.anterior
    if ($tipo -notin 'oficinas','responsables' -or $null -eq $anterior) { throw 'Catálogo o registro anterior inválido' }
    $id = [int]$anterior.id
    $alias = if ($tipo -eq 'oficinas') { 'ofi' } else { 'res' }
    $codigoCampo = if ($tipo -eq 'oficinas') { 'CODOFIC' } else { 'CODRESP' }
    $mapa = if ($tipo -eq 'oficinas') { [ordered]@{id='CODOFIC';nombre='NOMOFIC'} } else { [ordered]@{id='CODRESP';nombre='NOMRESP';cargo='CARGO';oficinaId='CODOFIC'} }
    $lineas = [Collections.Generic.List[string]]::new()
    $lineas.Add(@"
CLOSE TABLES ALL
SET EXCLUSIVE OFF
SET MULTILOCKS ON
SET DELETED ON
SET REPROCESS TO 1 SECONDS
USE actual SHARED IN 0 ALIAS af
USE oficina SHARED IN 0 ALIAS ofi
USE resp SHARED IN 0 ALIAS res
IF !FLOCK('af') OR !FLOCK('ofi') OR !FLOCK('res')
    RETURN 'ERROR: Hay datos en edición; guarda o cancela los cambios en VSIAF'
ENDIF
IF !MAKETRANSACTABLE('$alias')
    RETURN 'ERROR: No se pudo iniciar la eliminación en FoxPro'
ENDIF
BEGIN TRANSACTION
SELECT $alias
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND $codigoCampo=$id
IF !FOUND()
    RETURN 'ERROR: El registro ya fue eliminado en VSIAF; se volverá a consultar'
ENDIF
"@)
    foreach ($k in $mapa.Keys) {
        $campo = $mapa[$k]
        $valor = $anterior.$k
        $expresion = if ($valor -is [string]) { 'ALLTRIM(NVL('+ $campo +',""))' } else { $campo }
        $lineas.Add('IF !('+ $expresion +' == '+(Literal $valor)+")`nRETURN 'ERROR: El catálogo cambió en VSIAF; no se eliminó'`nENDIF")
    }
    $lineas.Add(@"
SELECT af
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND $codigoCampo=$id
IF FOUND()
    RETURN 'ERROR: El registro tiene activos asignados; no se eliminó'
ENDIF
"@)
    if ($tipo -eq 'oficinas') {
        $lineas.Add(@"
SELECT res
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND CODOFIC=$id
IF FOUND()
    RETURN 'ERROR: La oficina tiene responsables registrados; no se eliminó'
ENDIF
"@)
    }
    $lineas.Add("DELETE IN $alias`nEND TRANSACTION`nFLUSH`nRETURN 'OK'")
    $cmd = $conexion.CreateCommand()
    $cmd.CommandText = 'EXECSCRIPT(?)'
    [void]$cmd.Parameters.Add('script',[System.Data.OleDb.OleDbType]::LongVarChar)
    $cmd.Parameters[0].Value = $lineas -join "`r`n"
    try { $resultado = $cmd.ExecuteScalar(); if ($resultado -ne 'OK') { throw $resultado } }
    finally {
        $cmd.Parameters[0].Value = "IF TXNLEVEL()>0`nROLLBACK`nENDIF`nUNLOCK ALL`nCLOSE TABLES ALL`nRETURN .T."
        try { [void]$cmd.ExecuteScalar() } finally { $cmd.Dispose() }
    }
    return 'OK'
}

function Consulta($activos) {
    $carpeta = Join-Path (Split-Path -Parent $Directorio) 'consulta'
    [void](New-Item -ItemType Directory -Path $carpeta -Force)
    $archivo = Literal (Join-Path $carpeta 'activos.dbf')
    $script = [Collections.Generic.List[string]]::new()
    $script.Add(@"
CLOSE TABLES ALL
SET EXCLUSIVE OFF
SET MULTILOCKS ON
SET DELETED OFF
SET REPROCESS TO 1 SECONDS
IF !FILE($archivo)
    CREATE TABLE ($archivo) FREE (CODIGO C(15) NOT NULL, DESCRIP C(150) NOT NULL, COSTO N(15,2) NOT NULL, VIDAUTIL N(6,2) NOT NULL, FECHA D NOT NULL, ESTADO C(10) NOT NULL, ENCARGADO C(35) NOT NULL)
    USE
ENDIF
USE ($archivo) SHARED IN 0 ALIAS consulta
IF !FLOCK('consulta')
    RETURN 'ERROR: Cierra la edición de la tabla de consulta'
ENDIF
IF !MAKETRANSACTABLE('consulta')
    RETURN 'ERROR: No se pudo abrir la tabla de consulta'
ENDIF
BEGIN TRANSACTION
SELECT consulta
DELETE ALL
"@)
    foreach ($a in $activos) {
        $codigo = Literal $a.codigo
        $script.Add("LOCATE FOR ALLTRIM(CODIGO)==$codigo")
        $script.Add("IF !FOUND()`r`nAPPEND BLANK`r`nELSE`r`nRECALL`r`nENDIF")
        $fecha = [datetime]::ParseExact($a.fechaIncorporacion,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
        $valores = [ordered]@{CODIGO=$a.codigo;DESCRIP=$a.descripcion;COSTO=$a.costoInicial;VIDAUTIL=$a.vidaUtil;ESTADO=$a.estadoActivo;ENCARGADO=$a.responsable}
        $asignaciones = @($valores.Keys | ForEach-Object { $_ + ' WITH ' + (Literal $valores[$_]) })
        $script.Add('REPLACE '+($asignaciones -join ',')+',FECHA WITH DATE('+$fecha.Year+','+$fecha.Month+','+$fecha.Day+')')
    }
    $script.Add("END TRANSACTION`r`nFLUSH`r`nUNLOCK ALL`r`nCLOSE TABLES ALL`r`nSET DELETED ON`r`nRETURN 'OK'")
    $cmd = $conexion.CreateCommand()
    $cmd.CommandText = 'EXECSCRIPT(?)'
    [void]$cmd.Parameters.Add('script',[System.Data.OleDb.OleDbType]::LongVarChar)
    $cmd.Parameters[0].Value = $script -join "`r`n"
    try { $resultado = $cmd.ExecuteScalar(); if ($resultado -ne 'OK') { throw $resultado } }
    finally {
        $cmd.Parameters[0].Value = "IF TXNLEVEL()>0`r`nROLLBACK`r`nENDIF`r`nUNLOCK ALL`r`nCLOSE TABLES ALL`r`nSET DELETED ON`r`nRETURN .T."
        try { [void]$cmd.ExecuteScalar() } finally { $cmd.Dispose() }
    }
    return 'OK'
}

function Guardar($peticion) {
    $a = $peticion.activo
    $anterior = $peticion.anterior
    $codigo = Literal $peticion.codigo
    $lineas = [Collections.Generic.List[string]]::new()
    $lineas.Add(@'
CLOSE TABLES ALL
SET EXCLUSIVE OFF
SET MULTILOCKS ON
SET DELETED ON
SET REPROCESS TO 1 SECONDS
USE actual SHARED IN 0 ALIAS af
USE oficina SHARED IN 0 ALIAS ofi
USE resp SHARED IN 0 ALIAS res
USE auxiliar SHARED IN 0 ALIAS aux
IF !FLOCK("af") OR !FLOCK("ofi") OR !FLOCK("res") OR !FLOCK("aux")
    RETURN "ERROR: FoxPro tiene un registro en edición; guarda o cancela los cambios"
ENDIF
IF !MAKETRANSACTABLE("af") OR !MAKETRANSACTABLE("ofi") OR !MAKETRANSACTABLE("res") OR !MAKETRANSACTABLE("aux")
    RETURN "ERROR: No se pudo iniciar una transacción de FoxPro"
ENDIF
BEGIN TRANSACTION
SELECT af
'@)
    $lineas.Add("LOCATE FOR ALLTRIM(CODIGO)==$codigo AND ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1'")
    if ($null -eq $anterior) {
        $lineas.Add('IF FOUND()'+"`n"+'RETURN "ERROR: El activo ya existe en FoxPro"'+"`n"+'ENDIF')
    } else {
        $lineas.Add('IF !FOUND()'+"`n"+'RETURN "ERROR: El activo fue eliminado en FoxPro"'+"`n"+'ENDIF')
        foreach ($p in $anterior._native.PSObject.Properties) {
            if ($p.Name -notmatch '^[A-Z_]+$') { throw 'Campo inválido' }
            $expr = if ($p.Value -is [string]) { 'ALLTRIM(NVL('+$p.Name+',""))' } else { $p.Name }
            $lineas.Add('IF !('+ $expr +' == '+(Literal $p.Value)+')'+"`n"+'RETURN "ERROR: El activo cambió en FoxPro durante la operación"'+"`n"+'ENDIF')
        }
        $lineas.Add('IF API_ESTADO <> 1'+"`n"+'RETURN "ERROR: El activo no está en ELABORADO"'+"`n"+'ENDIF')
    }
    if ($null -eq $a) {
        $lineas.Add('DELETE IN af')
    } else {
        $grupos = Filas 'SELECT codcont,nombre,depreciar,actualizar FROM codcont'
        $g = @($grupos.Rows | Where-Object { (Texto $_.NOMBRE) -ceq $a.grupo })
        if ($g.Count -ne 1) { throw 'El grupo no existe en FoxPro' }
        if ([bool]$g[0].DEPRECIAR -ne [bool]$a.depreciar -or [bool]$g[0].ACTUALIZAR -ne [bool]$a.actualizar) { throw 'Las opciones no coinciden con el grupo de FoxPro' }
        $grupo = [int]$g[0].CODCONT
        $estados = Filas 'SELECT codestado,nomestado FROM estado'
        $e = @($estados.Rows | Where-Object { (Texto $_.NOMESTADO) -ceq $a.estadoActivo })
        if ($e.Count -ne 1 -or $a.estadoRegistro -ne 'ELABORADO') { throw 'Estado no compatible con FoxPro' }
        $oficina = [int]$a.oficinaId
        $responsable = [int]$a.responsableId
        $nomOfi = Literal $a.oficina
        $nomResp = Literal $a.responsable
        $cargo = Literal $a.cargo
        $auxiliar = Literal $a.auxiliar
        $oldOfi = Literal $anterior.oficina
        $oldResp = Literal $anterior.responsable
        $oldCargo = Literal $anterior.cargo
        $lineas.Add(@"
SELECT ofi
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND CODOFIC=$oficina
IF FOUND()
    IF !(ALLTRIM(NOMOFIC)==$nomOfi) AND !(ALLTRIM(NOMOFIC)==$oldOfi)
        RETURN "ERROR: La oficina cambió en FoxPro"
    ENDIF
    REPLACE NOMOFIC WITH $nomOfi
ELSE
    APPEND BLANK IN ofi
    REPLACE ENTIDAD WITH '0143',UNIDAD WITH '1',CODOFIC WITH $oficina,NOMOFIC WITH $nomOfi,FEULT WITH DATE(),USUAR WITH 'admin' IN ofi
    IF TYPE('ofi.API_ESTADO') <> 'U'
        REPLACE API_ESTADO WITH 1 IN ofi
    ENDIF
ENDIF
SELECT res
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND CODOFIC=$oficina AND CODRESP=$responsable
IF FOUND()
    IF !(ALLTRIM(NOMRESP)==$nomResp) AND !(ALLTRIM(NOMRESP)==$oldResp)
        RETURN "ERROR: El responsable cambió en FoxPro"
    ENDIF
    IF !(ALLTRIM(CARGO)==$cargo) AND !(ALLTRIM(CARGO)==$oldCargo)
        RETURN "ERROR: El cargo cambió en FoxPro"
    ENDIF
    REPLACE NOMRESP WITH $nomResp, CARGO WITH $cargo
ELSE
    APPEND BLANK IN res
    REPLACE ENTIDAD WITH '0143',UNIDAD WITH '1',CODOFIC WITH $oficina,CODRESP WITH $responsable,NOMRESP WITH $nomResp,CARGO WITH $cargo,FEULT WITH DATE(),USUAR WITH 'admin' IN res
    IF TYPE('res.API_ESTADO') <> 'U'
        REPLACE API_ESTADO WITH 1 IN res
    ENDIF
ENDIF
SELECT aux
LOCATE FOR ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND CODCONT=$grupo AND ALLTRIM(NOMAUX)==$auxiliar
LOCAL auxiliarCodigo
IF FOUND()
    auxiliarCodigo = CODAUX
ELSE
    SELECT MAX(CODAUX) AS ultimo FROM aux WHERE ALLTRIM(ENTIDAD)=='0143' AND ALLTRIM(UNIDAD)=='1' AND CODCONT=$grupo INTO CURSOR siguiente
    auxiliarCodigo = NVL(siguiente.ultimo,0) + 1
    USE IN siguiente
    IF auxiliarCodigo > 999
        RETURN "ERROR: No quedan códigos de auxiliar"
    ENDIF
    APPEND BLANK IN aux
    REPLACE ENTIDAD WITH '0143',UNIDAD WITH '1',CODCONT WITH $grupo,CODAUX WITH auxiliarCodigo,NOMAUX WITH $auxiliar IN aux
ENDIF
SELECT af
"@)
        $fecha = [datetime]::ParseExact($a.fechaIncorporacion,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
        $valores = [ordered]@{
            CODIGO=$a.codigo; ENTIDAD='0143'; UNIDAD='1'; CODCONT=$grupo; VIDAUTIL=$a.vidaUtil; DESCRIP=$a.descripcion
            COSTO=$a.costoInicial; DEPACU=$a.depreciacionInicial; DIA=$fecha.Day; MES=$fecha.Month; ANO=$fecha.Year
            CODOFIC=$oficina; CODRESP=$responsable; OBSERV=$a.observaciones; CODESTADO=[int]$e[0].CODESTADO
            COD_RUBE=$a.rube; NRO_CONV=$a.numeroConvenio; ORG_FIN=$a.organismoFinanciador; API_ESTADO=1; CODIGOSEC=$a.codigoAdicional
        }
        $campos = @($valores.Keys)
        $expresiones = @($valores.Values | ForEach-Object { Literal $_ })
        if ($null -eq $anterior) {
            $lineas.Add('APPEND BLANK IN af')
            $asignaciones = @($campos | ForEach-Object { $_+' WITH '+(Literal $valores[$_]) })
            $lineas.Add('REPLACE '+($asignaciones -join ',')+',CODAUX WITH auxiliarCodigo,FEULT WITH DATE(),USUAR WITH "admin",BANDERAS WITH "0" IN af')
        } else {
            $asignaciones = @($campos | ForEach-Object { $_+' WITH '+(Literal $valores[$_]) })
            $lineas.Add('REPLACE '+($asignaciones -join ',')+',CODAUX WITH auxiliarCodigo,FEC_MOD WITH DATE(),USU_MOD WITH "admin" IN af')
        }
    }
    $lineas.Add(@'
END TRANSACTION
FLUSH
RETURN "OK"
'@)
    $cmd = $conexion.CreateCommand()
    $cmd.CommandText = 'EXECSCRIPT(?)'
    [void]$cmd.Parameters.Add('script',[System.Data.OleDb.OleDbType]::LongVarChar)
    $cmd.Parameters[0].Value = $lineas -join "`r`n"
    try { $resultado = $cmd.ExecuteScalar(); if ($resultado -ne 'OK') { throw $resultado } }
    finally {
        $limpiar = $conexion.CreateCommand()
        $limpiar.CommandText = 'EXECSCRIPT(?)'
        [void]$limpiar.Parameters.Add('script',[System.Data.OleDb.OleDbType]::LongVarChar)
        $limpiar.Parameters[0].Value = "IF TXNLEVEL() > 0`r`nROLLBACK`r`nENDIF`r`nUNLOCK ALL`r`nCLOSE TABLES ALL`r`nRETURN .T."
        try { [void]$limpiar.ExecuteScalar() } finally { $limpiar.Dispose(); $cmd.Dispose() }
    }
    return 'OK'
}

try {
    while ($null -ne ($linea = [Console]::ReadLine())) {
        try {
            $peticion = $linea | ConvertFrom-Json
            if ($peticion.accion -eq 'leer') { $datos = Leer }
            elseif ($peticion.accion -eq 'guardar') { $datos = Guardar $peticion }
            elseif ($peticion.accion -eq 'responsables') { $datos = Responsables }
            elseif ($peticion.accion -eq 'catalogos') { $datos = Catalogos }
            elseif ($peticion.accion -eq 'guardar-catalogo') { $datos = GuardarCatalogo $peticion }
            elseif ($peticion.accion -eq 'eliminar-catalogo') { $datos = EliminarCatalogo $peticion }
            elseif ($peticion.accion -eq 'consulta') { $datos = Consulta $peticion.activos }
            else { throw 'Operación desconocida' }
            $salida = @{ok=$true; resultado=$datos}
        } catch { $salida = @{ok=$false; error=$_.Exception.Message} }
        [Console]::WriteLine(($salida | ConvertTo-Json -Depth 8 -Compress))
    }
} finally { $conexion.Dispose() }
