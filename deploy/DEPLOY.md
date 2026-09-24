# 图书借阅系统 · 部署手册（Windows 本机版）

> 目标形态：这台电脑长期开机当服务器，局域网内任意机器 / 一体机打开浏览器就能用。
> 全程 8 步，约 30 分钟（首次；不含下载 nginx 和 Maven 打包时间）。
> 一键脚本已放在 `D:\RuoYi-Vue-v3.9.2\deploy\` 下，配套文件都在那儿。

---

## 0. 先理解：现在跑的和部署后跑的，差在哪

| | 现在（开发态） | 部署后（生产态） |
| --- | --- | --- |
| 后端怎么起 | IDEA 里点绿三角 | 双击 `start-all.bat`，用 `java -jar` 跑两个成品包 |
| 前端怎么起 | `npm run dev`（80 / 81 端口） | 打好 `dist`，交给 nginx 托管 |
| 谁能访问 | 只有你自己这台机器 | 局域网所有机器 / 一体机 |
| 电脑重启后 | 全部要手动重开 | 加进开机启动，自动恢复 |
| 改了代码 | 立刻生效 | 必须重新打包再替换 |

打个比方：

- **jar 包** = 做好的菜，微波炉（java）一热就能上桌，不依赖 IDEA；
- **dist** = 印好的宣传册，本身不会自己见客，需要个门房摆出去；
- **nginx** = 前台接待。客人（浏览器）只找它：要页面的请求它给宣传册，要数据的请求它转到后厨（jar）。

**为什么省不掉 nginx**：宣传册（dist）里印的所有联系方式都写着"找前台"（`/prod-api/...`），
没有前台就没人接这个茬；而且浏览器不允许页面直接跨端口去后厨拿数据（跨域限制）。
所以 **nginx 不是可选项，是两个前端能跑起来的前提**。

---

## 1. 部署后的样子

```
                    局域网内的电脑 / 一体机
                              │
                              ▼
              ┌───────────────────────────────┐
              │   nginx（这台机器，随开机启动）  │
              │   :80  → 管理后台页面           │
              │   :81  → 借阅终端页面           │
              └───────┬───────────────┬───────┘
        /prod-api/*   │               │  /prod-api/*
                      ▼               ▼
        ┌──────────────────┐  ┌──────────────────┐
        │ ruoyi-admin.jar  │  │ ruoyi-client.jar │
        │      :8080       │  │      :8081       │
        │ （管理端接口）    │  │ （终端接口）      │
        └────────┬─────────┘  └────────┬─────────┘
                 └──────────┬──────────┘
                            ▼
                 MySQL :3306 + Redis :6379
```

关键点：**两个后端是两套独立进程**，共用同一个数据库和 Redis，但登录密钥不同，
所以管理端的会话和终端的会话互不通用（这是刻意的安全设计，不是 bug）。

---

## 2. 开工前确认（清单）

| 项 | 要求 | 怎么确认 |
| --- | --- | --- |
| JDK | 17（已有，`D:\JDK`） | 命令行 `D:\JDK\bin\java -version` |
| Node / npm | Node 18 以上（本机 22 / 24 都行） | `node -v` |
| MySQL | 3306 在监听，库 `ry_vue` 有数据 | `netstat -ano \| findstr :3306` |
| Redis | 6379 在监听 | `netstat -ano \| findstr :6379` |
| nginx | **待装**（第 6 步） | — |
| 磁盘 | `D:\ruoyi` 预留几百 MB | — |

> ⚠️ **MySQL / Redis 要确认是否随开机自动启动**。如果 Redis 是手动双击 `redis-server.exe` 跑起来的，
> 电脑重启后它不会自己回来，后端会连不上缓存、登录直接失败。确认方式：
> `Win+R` 输入 `services.msc`，看列表里有没有 MySQL80 / Redis 之类的服务，状态是否为"自动"。

---

## 3. 第 1 步：停掉现在所有调试进程 ★最容易漏的一步

现在这台机器上 **80、81、8080、8081 四个端口全被占用**（IDEA 里两个服务 + 两个 dev server）。
不停掉它们，后面必然撞两堵墙：

- **端口被占**：jar 和 nginx 起不来；
- **文件被锁**：`mvn clean package` 删不掉 `target` 目录，打包直接失败。

要停的东西：

| 停什么 | 在哪儿停 |
| --- | --- |
| ruoyi-admin（8080） | IDEA 里点红色方块 |
| ruoyi-client（8081） | IDEA 里点红色方块 |
| ruoyi-ui dev server（80） | 那两个 `npm run dev` 的黑窗口，`Ctrl+C` |
| client-front dev server（81） | 同上 |

确认已经空出来（`LISTENING` 行里不该再出现 80 / 81 / 8080 / 8081）：

```
netstat -ano | findstr ":80 :81 :8080 :8081"
```

> 提示：`:80` 会连带匹配到 `:8080`，看具体端口号即可，别被中间这几行吓到。
> 输出里的最后一列是进程号（PID），想知道是谁占着某个端口：`tasklist | findstr <PID>`。

也可以用现成脚本：双击 `deploy\windows\stop-all.bat`（会按端口杀进程，**包括 IDEA 里的**）。

---

## 4. 第 2 步：打后端成品包

在命令行（cmd）里执行：

```bat
cd /d D:\RuoYi-Vue-v3.9.2
set JAVA_HOME=D:\JDK
"D:\IJ\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" clean package -DskipTests
```

> IDEA 用户也可在右侧 Maven 面板点 `Lifecycle → clean` 再点 `package`，效果一样。
> **必须带 `clean`**：以前编译留下的旧 `.class` 不清掉，会导致接口重复注册、改名后旧映射还在。

**判据**：最后看到 `BUILD SUCCESS`，且出现两个文件：

```
D:\RuoYi-Vue-v3.9.2\ruoyi-admin\target\ruoyi-admin.jar
D:\RuoYi-Vue-v3.9.2\ruoyi-client\target\ruoyi-client.jar
```

如果报 `BUILD FAILURE` 且提到"文件正在使用 / 无法删除"，说明第 1 步的进程没停干净，回去停。

---

## 5. 第 3 步：打前端成品包

两个前端各打一次（`build:prod` 会自动读取 `.env.production`，接口前缀变成 `/prod-api`）：

```bat
cd /d D:\RuoYi-Vue-v3.9.2\ruoyi-ui
npm run build:prod

cd /d D:\RuoYi-Vue-v3.9.2\client-front
npm run build:prod
```

**判据**：两条命令都正常结束，生成 `ruoyi-ui\dist\` 和 `client-front\dist\`。
产物里应能同时看到 `index.html` 和 `.js.gz`（gzip 压缩文件，nginx 会直接下发）。

> 如果 `npm run build:prod` 报 `vue-cli-service 不是内部或外部命令`，说明依赖没装，
> 先在这两个目录各跑一次 `npm install`。

---

## 6. 第 4 步：建部署目录、把成品搬进去

一次性建好目录（复制粘贴执行）：

```bat
mkdir D:\ruoyi\app  D:\ruoyi\web\admin  D:\ruoyi\web\client  D:\ruoyi\logs  D:\ruoyi\uploadPath

xcopy /E /I /Y D:\RuoYi-Vue-v3.9.2\ruoyi-ui\dist      D:\ruoyi\web\admin
xcopy /E /I /Y D:\RuoYi-Vue-v3.9.2\client-front\dist  D:\ruoyi\web\client
copy  /Y       D:\RuoYi-Vue-v3.9.2\ruoyi-admin\target\ruoyi-admin.jar   D:\ruoyi\app\
copy  /Y       D:\RuoYi-Vue-v3.9.2\ruoyi-client\target\ruoyi-client.jar D:\ruoyi\app\
```

搬完之后长这样：

```
D:\ruoyi\
  app\      ruoyi-admin.jar、ruoyi-client.jar、start-all.bat、stop-all.bat
  web\
    admin\  ← ruoyi-ui 的 dist（管理后台页面）
    client\ ← client-front 的 dist（借阅终端页面）
  logs\     ← 后端控制台日志
  uploadPath\ ← 上传文件落地目录（后端配置里写死为 D:/ruoyi/uploadPath）
```

把 `deploy\windows\start-all.bat`、`stop-all.bat` 也拷到 `D:\ruoyi\app\` 下备用。

---

## 7. 第 5 步：装并配置 nginx

1. 下载 Windows 版：<http://nginx.org/en/download.html> 选 `nginx/Windows-1.27.x`（zip）。
2. 解压到 **`D:\nginx`**（路径别带空格和中文）。
3. 把我们准备好的配置文件覆盖过去：

```bat
copy /Y D:\RuoYi-Vue-v3.9.2\deploy\nginx\nginx.conf D:\nginx\conf\nginx.conf
```

4. 打开 `D:\nginx\conf\nginx.conf`，**只需改两处**（已在文件里用 ★ 标出）：

| 行 | 值 | 含义 |
| --- | --- | --- |
| `root D:/ruoyi/web/admin;` | 保持不变 | 管理后台页面目录 |
| `root D:/ruoyi/web/client;` | 保持不变 | 借阅终端页面目录 |

（若你按第 6 步的路径照做，**这两处不用改**，直接可用。注意路径用正斜杠 `/`。）

5. 检查配置语法：

```bat
cd /d D:\nginx
nginx.exe -t
```

**判据**：输出 `syntax is ok` + `test is successful`。

---

## 8. 第 6 步：启动

最简单：去 `D:\ruoyi\app\` 双击 **`start-all.bat`**，它会依次拉起两个 jar + nginx。

手工启动也可以：

```bat
start "ruoyi-admin"  /min D:\JDK\bin\java.exe -Xmx1024m -jar D:\ruoyi\app\ruoyi-admin.jar
start "ruoyi-client" /min D:\JDK\bin\java.exe -Xmx1024m -jar D:\ruoyi\app\ruoyi-client.jar
start "nginx" /D D:\nginx nginx.exe
```

**判据**：等 30 秒左右，两条命令都应返回"端口在听"：

```bat
netstat -ano | findstr ":80 :81 :8080 :8081"
```

**本机先自测**：

| 地址 | 应看到 |
| --- | --- |
| <http://localhost/> | 管理后台登录页（有验证码） |
| <http://localhost:81/> | 借阅终端首页（图书列表，可匿名浏览） |

浏览器按 `Ctrl+F5` 强刷，避免缓存干扰。

---

## 9. 第 7 步：放行防火墙（局域网访问的关键）

不做这一步，**只有这台机器自己能打开，别的电脑一律连不上**。

先查本机局域网 IP：命令行执行 `ipconfig`，找"IPv4 地址"，形如 `192.168.1.50`。

以**管理员身份**打开 cmd（右键"以管理员身份运行"），执行：

```bat
netsh advfirewall firewall add rule name="RuoYi-Web-80" dir=in action=allow protocol=TCP localport=80
netsh advfirewall firewall add rule name="RuoYi-Web-81" dir=in action=allow protocol=TCP localport=81
```

然后从**另一台电脑或手机**（连同一个 WiFi / 局域网）打开：

| 使用者 | 地址 |
| --- | --- |
| 管理员 | `http://192.168.1.50/` |
| 囚犯（终端一体机） | `http://192.168.1.50:81/` |

打不开时按顺序查：① 两台机器是否同一网段（IP 前三段相同）；② 防火墙规则是否加成功；
③ 这台机器 80/81 是否在监听；④ 是不是被公司网络策略挡了。

---

## 10. 第 8 步：设成开机自动启动

**方式一（最简单）**

1. `Win+R` → 输入 `shell:startup` → 回车，打开"启动"文件夹；
2. 把 `D:\ruoyi\app\start-all.bat` 的**快捷方式**拖进去。

缺点：必须登录 Windows 桌面才生效。

**方式二（真正的开机自启）**

1. `Win+R` → `taskschd.msc` → 创建任务；
2. 常规：勾"不管用户是否登录都要运行"；触发器：`启动时`；操作：启动程序 `D:\ruoyi\app\start-all.bat`；
3. 条件：取消"只有在计算机使用交流电源时才启动"。

**另外**：MySQL、Redis 也必须是自动启动的服务（见第 2 步的确认方法），否则重启后后端连不上数据库。

---

## 11. 日常运维

| 场景 | 怎么做 |
| --- | --- |
| 只改了后端代码 | 停服务 → `mvn clean package` → 用新 jar 覆盖 `D:\ruoyi\app\` → `start-all.bat` |
| 只改了前端代码 | 对应前端 `npm run build:prod` → 覆盖 `D:\ruoyi\web\admin`（或 `client`）目录 → 浏览器强刷 |
| 看后端日志 | 控制台在 `D:\ruoyi\logs\`；**业务日志在 `D:\home\ruoyi\logs\`**（见下方提示） |
| 看 nginx 日志 | `D:\nginx\logs\access.log`、`error.log` |
| 改配置不想重打包 | 在 jar 同级建 `config\application.yml` 写要覆盖的项，重启即生效 |
| 想回去改代码调试 | 先 `stop-all.bat`（否则 nginx 占着 80 / 81 起不来），改完再按第 4~6 步重打一次包覆盖 |
| 全部停掉 | 双击 `stop-all.bat` |

> ⚠️ **Windows 上业务日志的位置有点反直觉**：`logback.xml` 里写的是 `/home/ruoyi/logs`，
> 在 Windows 上会被解释成"当前盘符下的 `\home\ruoyi\logs`"，所以实际落在
> **`D:\home\ruoyi\logs`**（sys-info.log / sys-error.log / sys-user.log）。
> 排查问题去那里找，别在项目目录里瞎翻。

---

## 12. 将来搬到 Linux 服务器，要改的只有 4 处

部署包不用重做，改动集中在下面这些点。`deploy\linux\` 已备好两份 systemd 服务文件。

| # | 改什么 | 从 | 到 |
| --- | --- | --- | --- |
| 1 | 上传目录（`ruoyi.profile`） | `D:/ruoyi/uploadPath` | `/home/ruoyi/uploadPath` |
| 2 | nginx 里两处 `root` | `D:/ruoyi/web/admin` | `/home/ruoyi/web/admin` |
| 3 | systemd 文件里的路径 | `D:/ruoyi/app` | `/home/ruoyi/app` |
| 4 | 数据库 / Redis 地址 | `localhost` | 同机则不用改；不同机改成服务器 IP |

**第 1 处不用重新打包**，在 jar 同级建 `config/application.yml` 即可覆盖：

```yaml
ruoyi:
  profile: /home/ruoyi/uploadPath
```

> 注意：`ruoyi.profile` 是**两个 jar 各自**的 `application.yml` 里都有的配置项，两端都要覆盖。
> `logback.xml` 的 `/home/ruoyi/logs` 在 Linux 上正好是对的，无需改动。

Linux 上的起停（systemd）：

```bash
sudo cp deploy/linux/ruoyi-admin.service /etc/systemd/system/
sudo cp deploy/linux/ruoyi-client.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now ruoyi-admin ruoyi-client
sudo systemctl status ruoyi-admin          # 看是否 active (running)
```

如果是**换了一台全新的机器**，数据库要先初始化，按顺序执行：

```
sql/ry_20260320.sql       ← 若依基础表 + 菜单
sql/quartz.sql            ← 定时任务表
sql/ssk_book_category.sql ← 下面四个是本次业务的表，都是可重复执行的
sql/ssk_book.sql
sql/ssk_borrow_record.sql
sql/ssk_borrow_request.sql
sql/ssk_prisoner_account.sql
```

这几份脚本都**不写 `USE` 语句**，必须显式指定库名：

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 ry_vue < sql/ssk_book.sql
```

---

## 13. 常见故障对照表

| 现象 | 严重性 | 原因 | 处理 |
| --- | --- | --- | --- |
| 页面能开，一刷新就 404 | 阻断 | 前端是 history 路由，nginx 少了回落规则 | 确认 `location /` 里有 `try_files $uri $uri/ /index.html;` |
| 页面能开，接口全 404 | 阻断 | 反代前缀或端口写错 | 确认 `location ^~ /prod-api/` 的 `proxy_pass` 指向 8080（后台）/ 8081（终端） |
| 头像、上传的图片打不开 | 建议 | `location ^~` 少了 `^~`，正则 location 抢走了 `/prod-api/` 请求 | 确认写的是 `location ^~ /prod-api/`，不是 `location /prod-api/` |
| 接口返回 HTTP 200 但提示未登录 | 提示 | 若依认证失败**不改状态码**，看响应体里的 `code: 401` | 属正常，重新登录即可 |
| 本机能开、别的电脑打不开 | 阻断 | 防火墙没放行，或不在同一网段 | 见第 9 步 |
| 8080 / 8081 起不来 | 阻断 | 端口被 IDEA 里的服务或 dev server 占着 | 跑 `stop-all.bat`，或 `netstat -ano \| findstr :8080` 查占用者 |
| `mvn clean` 报文件被占用 | 阻断 | 调试进程没停干净，target 被锁 | 停掉全部 java 进程后重试 |
| 重启电脑后全打不开 | 阻断 | 没设开机自启，或 MySQL / Redis 没起来 | 见第 10 步 |
| 登录后立刻提示"登录状态已过期" | 建议 | Redis 没跑 / 被清空（会话存在 Redis 里） | 确认 6379 在监听 |
| nginx 启动闪一下就没了 | 阻断 | 配置语法错，或 80 端口被 IIS 占用 | `nginx.exe -t` 看报错；`netstat -ano \| findstr :80` 查占用者 |

---

## 14. 备选：不想装 nginx 行不行

可以，但要接受代价，**不推荐**：

把 dist 里的文件塞进 jar 的静态资源目录，让后端自己托管页面。这样要动的地方是：
前端得按"不带 `/prod-api` 前缀"重新构建、后端要加 history 路由的回落处理、两个前端就再也分不开端口。
改动量比装个 nginx（解压即用）大得多，而且以后加页面、加端口都得再折腾一遍。

结论：**装 nginx 是最省事的那条路。**

---

## 附：本手册配套文件

| 文件 | 用途 |
| --- | --- |
| `deploy\nginx\nginx.conf` | 现成的 nginx 配置（80 后台 / 81 终端，含反代与缓存规则） |
| `deploy\windows\start-all.bat` | 一键启动两个 jar + nginx |
| `deploy\windows\stop-all.bat` | 一键停止（含端口清理） |
| `deploy\linux\ruoyi-admin.service` | Linux 上管理后台的 systemd 服务 |
| `deploy\linux\ruoyi-client.service` | Linux 上终端的 systemd 服务 |
