@echo off
title Compilador DutyCraft v1.0 - Java 17

echo ============================================
echo Compilador do Plugin DutyCraft
echo ============================================
echo.

echo Procurando Java 17 instalado...
echo.

set JDK_PATH=
for /d %%i in ("C:\Program Files\Java\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Java\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\AdoptOpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\OpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Amazon Corretto\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Microsoft\jdk-17*") do set JDK_PATH=%%i

if "%JDK_PATH%"=="" (
    echo ============================================
    echo ERRO: JDK 17 nao encontrado!
    echo Instale o Java 17 JDK e tente novamente.
    echo ============================================
    pause
    exit /b 1
)

echo Java 17 encontrado em: %JDK_PATH%
echo.

set JAVAC="%JDK_PATH%\bin\javac.exe"
set JAR="%JDK_PATH%\bin\jar.exe"

echo ============================================
echo Preparando ambiente de compilacao...
echo ============================================
echo.

echo Limpando pasta out...
if exist out (
    rmdir /s /q out >nul 2>&1
)
mkdir out
mkdir out\com
mkdir out\com\foxsrv
mkdir out\com\foxsrv\dutycraft

echo.
echo ============================================
echo Verificando dependencias...
echo ============================================
echo.

REM Verificar Spigot API
if not exist spigot-api-1.20.1-R0.1-SNAPSHOT.jar (
    echo [ERRO] spigot-api-1.20.1-R0.1-SNAPSHOT.jar nao encontrado!
    echo.
    echo Certifique-se de que o arquivo spigot-api-1.20.1-R0.1-SNAPSHOT.jar esta na pasta raiz.
    pause
    exit /b 1
) else (
    echo [OK] Spigot API encontrado
    set SPIGOT_PATH=spigot-api-1.20.1-R0.1-SNAPSHOT.jar
)

REM Verificar Vault API (opcional)
if not exist Vault.jar (
    echo [AVISO] Vault.jar nao encontrado na pasta raiz!
    echo O plugin DutyCraft nao requer Vault, mas pode ser usado para futuras integracoes.
    echo Continuando compilacao normalmente...
    echo.
    set VAULT_PATH=
) else (
    echo [OK] Vault API encontrado (opcional)
    set VAULT_PATH=Vault.jar
)

echo.
echo ============================================
echo Compilando DutyCraft...
echo ============================================
echo.

REM Montar classpath
set CLASSPATH="%SPIGOT_PATH%"
if defined VAULT_PATH (
    set CLASSPATH=%CLASSPATH%;"%VAULT_PATH%"
)

REM Mostrar classpath para debug
echo Classpath: %CLASSPATH%
echo.

REM Verificar se o arquivo fonte existe
if not exist src\com\foxsrv\dutycraft\DutyCraft.java (
    echo ============================================
    echo ERRO: Arquivo fonte nao encontrado!
    echo ============================================
    echo.
    echo Caminho esperado: src\com\foxsrv\dutycraft\DutyCraft.java
    echo.
    echo Estrutura de diretorios atual:
    echo.
    if exist src (
        echo Conteudo de src:
        dir /s /b src
    ) else (
        echo Pasta src nao encontrada!
    )
    echo.
    echo Criando estrutura de diretorios necessaria...
    mkdir src\com\foxsrv\dutycraft 2>nul
    echo Por favor, coloque o arquivo DutyCraft.java em src\com\foxsrv\dutycraft\
    pause
    exit /b 1
)

REM Criar arquivo com lista de fontes
dir /s /b src\com\foxsrv\dutycraft\*.java > sources.txt

REM Compilar com as dependências necessárias
echo Compilando DutyCraft.java...
%JAVAC% --release 17 -d out ^
-cp %CLASSPATH% ^
-sourcepath src ^
-encoding UTF-8 ^
@sources.txt

if %errorlevel% neq 0 (
    echo ============================================
    echo ERRO AO COMPILAR O PLUGIN!
    echo ============================================
    echo.
    echo Verifique os erros acima e corrija o codigo.
    echo.
    echo Possiveis causas:
    echo 1 - Erro de sintaxe no codigo
    echo 2 - Versao do Java incorreta
    echo 3 - Spigot API nao encontrada ou incompativel
    del sources.txt
    pause
    exit /b 1
)

del sources.txt

echo.
echo Compilacao concluida com sucesso!
echo.

echo ============================================
echo Copiando arquivos de recursos...
echo ============================================
echo.

REM Copiar plugin.yml
if exist resources\plugin.yml (
    copy resources\plugin.yml out\ >nul
    echo [OK] plugin.yml copiado
) else (
    echo [AVISO] plugin.yml nao encontrado em resources\
    echo Criando plugin.yml padrao...
    
    (
        echo name: DutyCraft
        echo version: 1.0
        echo main: com.foxsrv.dutycraft.DutyCraft
        echo api-version: 1.20
        echo author: FoxOficial2
        echo description: Sistema de inventarios separados para cargos e funcoes
        echo.
        echo commands:
        echo   setduty:
        echo     description: Cria um novo duty com o inventario atual
        echo     usage: /setduty ^<nome^>
        echo     permission: duty.admin
        echo   duty:
        echo     description: Entra ou sai de um duty
        echo     usage: /duty ^<nome^>
        echo.
        echo permissions:
        echo   duty.admin:
        echo     description: Permissao para criar duties
        echo     default: op
        echo   duty.*:
        echo     description: Permissao para usar todos os duties
        echo     default: op
        echo     children:
        echo       duty.teste: true
    ) > out\plugin.yml
    echo [OK] plugin.yml criado automaticamente
)

echo.
echo ============================================
echo Criando arquivo JAR...
echo ============================================
echo.

cd out

REM Criar JAR com todos os recursos
echo Criando DutyCraft.jar...
%JAR% cf DutyCraft.jar com plugin.yml

cd ..

echo.
echo ============================================
echo PLUGIN COMPILADO COM SUCESSO!
echo ============================================
echo.
echo Arquivo gerado: out\DutyCraft.jar
echo.
dir out\DutyCraft.jar
echo.
echo ============================================
echo RESUMO DA COMPILACAO:
echo ============================================
echo.
echo - Data/Hora: %date% %time%
echo - Java Version: 17
echo - Spigot API: OK
if defined VAULT_PATH (
    echo - Vault API: OK (opcional)
) else (
    echo - Vault API: NAO ENCONTRADO (opcional)
)
echo - Arquivo fonte: src\com\foxsrv\dutycraft\DutyCraft.java
echo.
echo ============================================
echo ARQUIVOS COMPILADOS:
echo ============================================
echo.
dir /b src\com\foxsrv\dutycraft\*.java
echo.
echo ============================================
echo REQUISITOS PARA EXECUCAO:
echo ============================================
echo.
echo 1 - Spigot/Paper 1.20+ necessario
echo 2 - Java 17 ou superior
echo 3 - Nenhuma dependencia externa obrigatoria
echo.
echo ============================================
echo Para instalar:
echo ============================================
echo.
echo 1 - Copie out\DutyCraft.jar para a pasta plugins do servidor
echo 2 - Reinicie o servidor ou use /reload confirm
echo 3 - Os dados serao salvos em plugins/DutyCraft/duties.yml e playerdata.yml
echo.
echo ============================================
echo COMANDOS DO PLUGIN:
echo ============================================
echo.
echo ADMIN:
echo /setduty ^<nome^> - Cria um novo duty com seu inventario atual
echo.
echo JOGADORES:
echo /duty ^<nome^> - Entra/sai de um duty
echo.
echo ============================================
echo PERMISSOES:
echo ============================================
echo.
echo duty.admin - Pode criar duties (default: op)
echo duty.* - Pode usar todos os duties (default: op)
echo duty.teste - Pode usar o duty "teste"
echo.
echo ============================================
echo FUNCIONALIDADES:
echo ============================================
echo.
echo - Sistema de inventarios separados para cargos/funcoes
echo - Permissoes individuais por duty: duty.nome
echo - Protecao total contra duplicacao de itens
echo - Nao pode jogar itens no chao
echo - Nao pode abrir baus ou containers
echo - Nao pode interagir com armor stands
echo - Nao pode colocar blocos
echo - Armadura protegida contra remocao/alteração
echo - Inventario original salvo ao entrar
echo - Inventario restaurado ao sair
echo - Dados salvos permanentemente em YAML
echo - Suporte a itens utilizaveis: comidas, pocoes, foguetes, armas
echo.
echo ============================================
echo.

pause