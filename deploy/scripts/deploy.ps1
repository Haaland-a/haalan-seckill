# =============================================================================
#  haalan 部署脚本（Windows 侧）—— 本机编译 + 同步到服务器 + 触发部署
#  ---------------------------------------------------------------------------
#  用途：在你自己的 Windows 上一条命令完成「改完代码 → 服务器上跑起来」。
#
#  用法（在 D:\dswork\work1\haalan\deploy\scripts 下）：
#      .\deploy.ps1                      # 全量：编译 + 同步 + 重建全部应用
#      .\deploy.ps1 gateway              # 只更新 gateway
#      .\deploy.ps1 gateway admin        # 更新多个
#      .\deploy.ps1 -SkipBuild           # 跳过本机 mvn，直接同步现有 jar
#      .\deploy.ps1 -Plan                # 只打印将要执行的命令，不做任何事
#
#  前置条件：
#      · 本机 JDK 17 + Maven（本机实测：E:\java\jdk-17.0.11、Maven 3.9.10）
#      · 本机 .m2 缓存已含依赖（实测 1.66 GB，够离线编译）
#      · 能 ssh 到 192.168.100.133（脚本用 SSH_ASKPASS 自动填密码）
#
#  为什么不直接在本机 docker build：
#      本机没装 Docker。本机只负责编译，镜像在服务器上构建。
#
#  实测速度（VMnet8 千兆内网）：
#      scp 上传 64.6 MB ≈ 1.1 秒（约 59 MB/s），6 个 jar 共约 700 MB ≈ 12 秒
#      本机 mvn -o package ≈ 2 分钟；服务器 mvn -o clean package ≈ 26 秒
#      → 如果你只想最快，可以直接在服务器上编译（见 deploy.sh），
#        省掉 700 MB 上传，而且服务器编译比本机还快。
# =============================================================================
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Services,

    [switch] $SkipBuild,
    [switch] $Plan
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------
$ServerHost = '192.168.100.133'
$ServerUser = 'root'
$Workspace  = 'D:\dswork\work1'
$BackendDir = Join-Path $Workspace 'haalan'
$DeployDir  = Join-Path $BackendDir 'deploy'
$RemoteDir  = '/root/haalan/deploy'
$RemoteRepo = '/root/haalan'

$AllServices = @('gateway','user-service','item-service','order-service','search-service','seckill-service','frontend','admin')
$BackendServices = @{
    'gateway'         = 'haalan-gateway'
    'user-service'    = 'user-service'
    'item-service'    = 'item-service'
    'order-service'   = 'order-service'
    'search-service'  = 'search-service'
    'seckill-service' = 'seckill-service'
}

if (-not $Services -or $Services.Count -eq 0) { $Services = $AllServices }
foreach ($s in $Services) {
    if ($AllServices -notcontains $s) {
        throw "未知服务 '$s'。可用: $($AllServices -join ', ')"
    }
}

# ---------------------------------------------------------------------------
# SSH 免交互：把密码交给 SSH_ASKPASS 辅助脚本
# ---------------------------------------------------------------------------
$AskPass = Join-Path $Workspace '_sshhelper\askpass.cmd'
if (-not (Test-Path $AskPass)) { throw "缺少 SSH 辅助脚本: $AskPass" }
$env:SSH_ASKPASS         = $AskPass
$env:SSH_ASKPASS_REQUIRE = 'force'
$env:DISPLAY             = 'localhost:0'

$SshOpts = @(
    '-o','StrictHostKeyChecking=no','-o','UserKnownHostsFile=NUL',
    '-o','PreferredAuthentications=password','-o','PubkeyAuthentication=no',
    '-o','NumberOfPasswordPrompts=1','-o','ConnectTimeout=15','-o','LogLevel=ERROR'
)
$Target = "$ServerUser@$ServerHost"

function Invoke-Remote {
    param([string] $Command)
    if ($Plan) { Write-Host "  [plan] ssh $Target `"$Command`"" -ForegroundColor DarkGray; return }
    & ssh @SshOpts $Target $Command
    if ($LASTEXITCODE -ne 0) { throw "远程命令失败 (exit $LASTEXITCODE): $Command" }
}

function Copy-ToRemote {
    param([string] $Local, [string] $Remote)
    if ($Plan) { Write-Host "  [plan] scp $Local -> $Target`:$Remote" -ForegroundColor DarkGray; return }
    & scp @SshOpts -r $Local $Target`:$Remote | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "scp 失败: $Local -> $Remote" }
}

Write-Host ''
Write-Host "=== haalan 部署: $($Services -join ', ') ===" -ForegroundColor Cyan
Write-Host ''

# ---------------------------------------------------------------------------
# 1. 本机编译后端（纯前端部署时跳过）
# ---------------------------------------------------------------------------
$needBackend = ($Services | Where-Object { $BackendServices.ContainsKey($_) }).Count -gt 0

if ($needBackend -and -not $SkipBuild) {
    Write-Host '==> 1/4 本机编译后端 (mvn -o clean package -DskipTests)'
    $env:JAVA_HOME = 'E:\java\jdk-17.0.11'
    if ($Plan) {
        Write-Host "  [plan] cd $BackendDir; mvn -o clean package -DskipTests" -ForegroundColor DarkGray
    } else {
        Push-Location $BackendDir
        try {
            & mvn -o clean package -DskipTests
            if ($LASTEXITCODE -ne 0) { throw 'Maven 编译失败，未做任何部署。' }
        } finally { Pop-Location }
    }
    Write-Host ''
} else {
    Write-Host '==> 1/4 跳过本机编译'
    Write-Host ''
}

# ---------------------------------------------------------------------------
# 2. 校验 jar 是否齐全
# ---------------------------------------------------------------------------
if ($needBackend) {
    Write-Host '==> 2/4 校验 jar'
    foreach ($s in $Services) {
        if (-not $BackendServices.ContainsKey($s)) { continue }
        $jar = Join-Path $BackendDir "$($BackendServices[$s])\target\$($BackendServices[$s])-0.0.1-SNAPSHOT.jar"
        if (-not (Test-Path $jar)) { throw "缺少 jar: $jar" }
        Write-Host ('  {0,-18} {1,10:N1} MB' -f $s, ((Get-Item $jar).Length / 1MB))
    }
    Write-Host ''
} else {
    Write-Host '==> 2/4 跳过 jar 校验'
    Write-Host ''
}

# ---------------------------------------------------------------------------
# 3. 同步到服务器
#    只同步 jar + 前端源码 + 部署脚本，不动服务器上的数据库与容器。
# ---------------------------------------------------------------------------
Write-Host '==> 3/4 同步到服务器'
Invoke-Remote "mkdir -p $RemoteDir/scripts"

foreach ($s in $Services) {
    if (-not $BackendServices.ContainsKey($s)) { continue }
    $name = $BackendServices[$s]
    $jar  = Join-Path $BackendDir "$name\target\$name-0.0.1-SNAPSHOT.jar"
    $dest = "$RemoteRepo/$name/target"
    Invoke-Remote "mkdir -p $dest"
    Write-Host "  -> $name"
    Copy-ToRemote $jar $dest
}

# 前端源码同步（排除 node_modules/dist/.git，构建在服务器容器里做）
foreach ($fe in @(@('haalan_','frontend'), @('haalan-admin','admin'))) {
    if ($Services -notcontains $fe[1]) { continue }
    Write-Host "  -> $($fe[0]) 源码"
    if ($Plan) {
        Write-Host "  [plan] scp $Workspace\$($fe[0]) -> $Target`:$(if($fe[1] -eq 'frontend'){'/root/haalan_'}else{'/root/haalan-admin'})" -ForegroundColor DarkGray
    } else {
        $remoteFe = if ($fe[1] -eq 'frontend') { '/root/haalan_' } else { '/root/haalan-admin' }
        & ssh @SshOpts $Target "mkdir -p $remoteFe" | Out-Null

        # 只同步构建真正需要的文件；node_modules / dist / .git / .idea 一律不同步
        # （Dockerfile 里的 .dockerignore 也会排除它们，但少传几百 MB 更划算）
        $feRoot = Join-Path $Workspace $fe[0]
        $feItems = @('src','package.json','package-lock.json','index.html',
                     'tsconfig.json','tsconfig.node.json','Dockerfile','nginx.conf',
                     '.env.development','.env.production','.dockerignore')
        # 两端的 vite 配置扩展名不同：前端 .js，管理端 .ts
        foreach ($v in 'vite.config.js','vite.config.ts') {
            if (Test-Path (Join-Path $feRoot $v)) { $feItems += $v }
        }
        if (Test-Path (Join-Path $feRoot 'public')) { $feItems += 'public' }

        $paths = $feItems | ForEach-Object { Join-Path $feRoot $_ } | Where-Object { Test-Path $_ }
        & scp @SshOpts -r @paths $Target`:$remoteFe 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "前端源码同步失败: $($fe[0])" }
    }
}

# 部署脚本与编排文件
Write-Host '  -> deploy 编排与脚本'
Copy-ToRemote (Join-Path $DeployDir 'docker-compose.apps.yml') "$RemoteDir/"
Copy-ToRemote (Join-Path $DeployDir '.env') "$RemoteDir/"
Copy-ToRemote (Join-Path $DeployDir 'scripts\deploy.sh') "$RemoteDir/scripts/"
Write-Host ''

# ---------------------------------------------------------------------------
# 4. 在服务器上构建并替换
# ---------------------------------------------------------------------------
Write-Host '==> 4/4 服务器构建并替换容器'
Invoke-Remote "sed -i 's/\r`$//' $RemoteDir/scripts/deploy.sh && chmod +x $RemoteDir/scripts/deploy.sh"
Invoke-Remote "cd $RemoteDir && bash scripts/deploy.sh --no-build $($Services -join ' ')"

Write-Host ''
Write-Host '=== 完成 ===' -ForegroundColor Green
