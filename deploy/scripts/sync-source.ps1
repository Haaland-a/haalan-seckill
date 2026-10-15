# =============================================================================
#  haalan 源码同步 + 服务器编译部署（Windows 侧）
#  ---------------------------------------------------------------------------
#  解决的问题：
#     deploy.sh 是在【服务器上】跑 mvn，编译的是 /root/haalan 里的源码。
#     如果你在本机改完代码直接跑 hd / deploy.sh，编译的还是服务器上的旧代码，
#     改动根本不会生效（本地 .ps1 老版本只同步 jar，不同步源码）。
#
#  本脚本做四件事：
#     1. 把本机 9 个后端模块的源码 + pom.xml，以及 2 个前端目录的源码
#        打成两个 tar.gz（后端约 170 KB，前端约 200 KB）
#     2. 上传到服务器 /tmp
#     3. 解包覆盖 /root/haalan 与 /root/haalan_、/root/haalan-admin
#        （只覆盖同名文件，不碰 target/、node_modules/、.git/、logs/）
#     4. 调用 deploy.sh 编译 + 重建指定应用
#
#  用法（在 D:\dswork\work1\haalan\deploy\scripts 下）：
#      .\sync-source.ps1                      # 同步源码 + 全量重建 9 个应用
#      .\sync-source.ps1 gateway              # 同步源码 + 只重建 gateway
#      .\sync-source.ps1 frontend             # 同步前端源码 + 重建前端
#      .\sync-source.ps1 -SyncOnly            # 只同步源码，不编译不部署
#      .\sync-source.ps1 -Plan                # 只打印将要做什么
#      .\sync-source.ps1 -NoSync gateway      # 跳过同步，只用服务器现有源码重建
#
#  与 deploy.ps1 的区别：
#      deploy.ps1   本机 mvn 编译 → 上传 jar（约 700 MB，本机编译约 2 分钟）
#      sync-source.ps1  上传源码（约 170 KB）→ 服务器编译（约 26 秒）
#    源码同步这条路更快，且保证编译的就是你刚改的代码。
# =============================================================================
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Services,

    [switch] $SyncOnly,
    [switch] $NoSync,
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
$RemoteRepo = '/root/haalan'
$RemoteTmp  = '/tmp/haalan-src.tar.gz'
$RemoteFeTmp = '/tmp/haalan-fe.tar.gz'

$AllServices = @('gateway','user-service','item-service','order-service','search-service','seckill-service','cart-service','frontend','admin')

# 需要同步源码的模块（后端，服务器上 mvn 编译）
$Modules = @('haalan-common','haalan-api','haalan-gateway',
             'user-service','item-service','order-service',
             'search-service','seckill-service','cart-service')

# 前端源码目录：容器内 npm build，不走 maven。
# 这两个目录以前不在同步范围内，导致「本机改了前端 → hd frontend → 服务器
# 还是旧代码」的 404 事故（Create.vue 从未上传）。现在纳入同步。
$FrontendDirs = @('haalan_', 'haalan-admin')

# 前端同步时要排除的目录（依赖与构建产物，服务器上会重新生成）
$FrontendExcludes = @('node_modules','dist','.git','.idea','.vite','coverage')

if (-not $Services -or $Services.Count -eq 0) { $Services = $AllServices }
foreach ($s in $Services) {
    if ($AllServices -notcontains $s) {
        throw "未知服务 '$s'。可用: $($AllServices -join ', ')"
    }
}

$Target  = "$ServerUser@$ServerHost"
$SshOpts = @('-o','StrictHostKeyChecking=no','-o','UserKnownHostsFile=NUL',
             '-o','ConnectTimeout=15','-o','LogLevel=ERROR','-o','ServerAliveInterval=30')

function Invoke-Remote {
    param([string] $Command)
    if ($Plan) { Write-Host "  [plan] ssh $Target `"$Command`"" -ForegroundColor DarkGray; return }
    & ssh @SshOpts $Target $Command
    if ($LASTEXITCODE -ne 0) { throw "远程命令失败 (exit $LASTEXITCODE): $Command" }
}

Write-Host ''
Write-Host "=== haalan 源码同步 + 部署: $($Services -join ', ') ===" -ForegroundColor Cyan
Write-Host ''

# ---------------------------------------------------------------------------
# 1. 打源码包
# ---------------------------------------------------------------------------
$tarball = Join-Path $env:TEMP 'haalan-src.tar.gz'
$feTarball = Join-Path $env:TEMP 'haalan-fe.tar.gz'
if (-not $NoSync) {
    Write-Host '==> 1/4 打包本机源码'
    if (Test-Path $tarball) { Remove-Item $tarball -Force }
    if (Test-Path $feTarball) { Remove-Item $feTarball -Force }

    # 只收 pom.xml 与各模块的 src/ 和 pom.xml —— 不含 target/ .git/ .idea/ logs/
    # 每个模块额外带上 Dockerfile，否则服务器上 mvn 能编出来但 compose build 找不到 Dockerfile
    # （实测 cart-service 首次部署就是卡在这里：failed to read dockerfile: open Dockerfile）
    $items = @('pom.xml')
    foreach ($m in $Modules) {
        $mp = Join-Path $BackendDir $m
        if (-not (Test-Path $mp)) { continue }
        if (Test-Path (Join-Path $mp 'src'))    { $items += "$m/src" }
        if (Test-Path (Join-Path $mp 'pom.xml')){ $items += "$m/pom.xml" }
        if (Test-Path (Join-Path $mp 'Dockerfile')) { $items += "$m/Dockerfile" }
    }

    # 部署配置也要同步：服务器上的 deploy.sh / docker-compose.apps.yml 是 deploy.sh
    # 自己要读的东西，以前不在同步清单里，导致「本机加了 cart-service → 服务器报
    # 未知服务 / no such service」，每次都得手工 scp。现在一并同步。
    # 注意只同步 apps 编排与 scripts，不同步 docker-compose.yml（那是中间件编排，
    # 覆盖服务器版本有风险）。
    if (Test-Path (Join-Path $BackendDir 'deploy/scripts'))            { $items += 'deploy/scripts' }
    if (Test-Path (Join-Path $BackendDir 'deploy/docker-compose.apps.yml')) { $items += 'deploy/docker-compose.apps.yml' }

    # 前端源码（含 package.json / vite 配置 / Dockerfile / nginx.conf）
    $feItems = @()
    foreach ($d in $FrontendDirs) {
        $dp = Join-Path $Workspace $d
        if (-not (Test-Path $dp)) { continue }
        $feItems += $d
    }

    if ($Plan) {
        Write-Host "  [plan] tar -czf $tarball -C $BackendDir <$($items.Count) 项>" -ForegroundColor DarkGray
        Write-Host "  [plan] tar -czf $feTarball -C $Workspace <$($feItems.Count) 项: $($feItems -join ', ') 排除 $($FrontendExcludes -join ',')>" -ForegroundColor DarkGray
    } else {
        & tar -czf $tarball -C $BackendDir @items
        if ($LASTEXITCODE -ne 0) { throw 'tar 打包失败（后端）' }
        $kb = [math]::Round((Get-Item $tarball).Length / 1KB, 1)
        $n  = (& tar -tzf $tarball).Count
        Write-Host ("  后端: {0} KB, {1} 个条目" -f $kb, $n)

        if ($feItems.Count -gt 0) {
            $exArgs = @()
            foreach ($e in $FrontendExcludes) { $exArgs += @('--exclude', $e) }
            & tar -czf $feTarball -C $Workspace @exArgs @feItems
            if ($LASTEXITCODE -ne 0) { throw 'tar 打包失败（前端）' }
            $fkb = [math]::Round((Get-Item $feTarball).Length / 1KB, 1)
            $fn  = (& tar -tzf $feTarball).Count
            Write-Host ("  前端: {0} KB, {1} 个条目" -f $fkb, $fn)
        }
    }
    Write-Host ''
} else {
    Write-Host '==> 1/4 跳过打包（-NoSync）'
    Write-Host ''
}

# ---------------------------------------------------------------------------
# 2. 上传
# ---------------------------------------------------------------------------
if (-not $NoSync) {
    Write-Host '==> 2/4 上传到服务器'
    if ($Plan) {
        Write-Host "  [plan] scp $tarball -> $Target`:$RemoteTmp" -ForegroundColor DarkGray
        Write-Host "  [plan] scp $feTarball -> $Target`:$RemoteFeTmp" -ForegroundColor DarkGray
    } else {
        & scp @SshOpts $tarball "$Target`:$RemoteTmp" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'scp 上传失败（后端）' }
        Write-Host "  -> $RemoteTmp"
        if (Test-Path $feTarball) {
            & scp @SshOpts $feTarball "$Target`:$RemoteFeTmp" | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'scp 上传失败（前端）' }
            Write-Host "  -> $RemoteFeTmp"
        }
    }
    Write-Host ''
} else {
    Write-Host '==> 2/4 跳过上传（-NoSync）'
    Write-Host ''
}

# ---------------------------------------------------------------------------
# 3. 解包覆盖（不删除任何东西，只覆盖同名源码文件）
# ---------------------------------------------------------------------------
if (-not $NoSync) {
    Write-Host '==> 3/4 解包覆盖服务器源码'
    # --overwrite 只覆盖包内已有的文件；不删服务器上多出来的文件
    Invoke-Remote "cd $RemoteRepo && tar -xzf $RemoteTmp --overwrite && rm -f $RemoteTmp && echo '  后端解包完成'"
    # 前端解包到 /root 下（包内第一层就是 haalan_ / haalan-admin）
    Invoke-Remote "cd /root && tar -xzf $RemoteFeTmp --overwrite && rm -f $RemoteFeTmp && echo '  前端解包完成'"
    Write-Host ''
} else {
    Write-Host '==> 3/4 跳过解包（-NoSync）'
    Write-Host ''
}

# ---------------------------------------------------------------------------
# 4. 服务器编译 + 重建容器
# ---------------------------------------------------------------------------
if ($SyncOnly) {
    Write-Host '==> 4/4 跳过部署（-SyncOnly）' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '源码已同步。要部署请再跑： hd <服务名>' -ForegroundColor Green
    exit 0
}

Write-Host '==> 4/4 服务器编译 + 重建容器'
Invoke-Remote "cd $RemoteRepo/deploy && bash scripts/deploy.sh $($Services -join ' ')"

Write-Host ''
Write-Host '=== 完成 ===' -ForegroundColor Green
