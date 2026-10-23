# BN 结构健康监测平台

Vue 3 数据大屏 + Spring Boot 3 后端 + SQL Server，界面按配套设计稿 `bn-monitor-platform.html` 一比一还原。

## 目录

```
D:\TransToGold
├── db\            建库脚本、种子数据、推送报文样例
├── server\        Spring Boot 3.3 / JDK 21 / MyBatis-Plus，打包产物 target\bn-server-1.0.0.jar
├── web\           Vue 3 + TS + Vite 5，样式取自设计稿的 oklch 令牌，图表为手写内联 SVG
└── bn-monitor-platform.html   设计稿原件（视觉基准，不参与构建）
```

## 启动顺序

### 1. 建库

不用 SSMS 也可以，库里已经建好了。要重建就在 `localhost\SQLEXPRESS01` 上按顺序执行：

- `db\01_schema.sql`（22 张表 + `PF_DAY` / `PF_MONTH` 分区，日期自动算，不用改脚本）
- `db\02_seed.sql`（4 设备 / 12 传感器 / 7803 条明细 / 19467 条小时聚合 / 4 报警 / 4 视频通道）

应用连接用独立账号 `bn_app`（密码 `Bn@Monitor2026`，`db_owner`），不用 sa：

```sql
CREATE LOGIN bn_app WITH PASSWORD='Bn@Monitor2026', CHECK_POLICY=OFF;
CREATE USER bn_app FOR LOGIN bn_app;
ALTER ROLE db_owner ADD MEMBER bn_app;
```

### 2. 后端

```bat
D:\ComposeMaven\apache-maven-3.9.9\bin\mvn.cmd -f server\pom.xml ^
  -Dmaven.repo.local=D:\ComposeMaven\repo -DskipTests package
java -jar server\target\bn-server-1.0.0.jar
```

Maven 装在 `D:\ComposeMaven`，本地仓库在 `D:\ComposeMaven\repo`（C 盘已满，别把仓库丢回 `C:\Users\admin\.m2`）。
端口在 `application.yml` 里是 `${BN_PORT:8090}`。**注意别用 `SERVER_PORT` 这个变量名**：Spring 会拿它覆盖 `server.port`。

### 3. 前端

```bat
cd web
npm install
npm run dev
```

访问 `http://localhost:5173`，默认账号 `admin` / `admin123`。

安装依赖时若 C 盘空间紧张，把临时目录挪走即可：

```bat
set TEMP=D:\Temp& set TMP=D:\Temp& set npm_config_cache=D:\npm-cache& npm install
```

## 端口与账号

| 项 | 值 |
|---|---|
| 后端 | 8090（本机 8080 被 Steam 占用），接口前缀 `/api/v1`，接收端 `/open/bn/push` |
| 前端 | 由后端同源托管，只访问 `http://localhost:8090`（页面、`/api`、`/open` 同一地址，登录一次） |
| 数据库 | `127.0.0.1:1433`（命名实例直连端口，本机没装 SQL Browser，不能用 `localhost\SQLEXPRESS01`），库名 `bn_monitor` |
| 应用账号 | `bn_app` / `Bn@Monitor2026`；后台 `admin` / `admin123` |

前端静态资源由后端直接托管：`WebConfig` 把 `/**` 指到 `web/dist`（其次 `classpath:/static`），`/` 转发到 `index.html`。
改完前端只要重新构建一次，刷新页面即可，不用重打 jar：

```bash
cd web && node node_modules/vite/bin/vite.js build
```

## 界面

6 个页面全部按设计稿实现，没有引入组件库，也没有用 ECharts：

导航是左侧一条悬浮的竖向按钮栏（宽度 64px、两端 32px 满圆、每项 42px 圆形按钮、磨砂半透明底），
6 个入口一条竖列排到底，不分段也不加分组标题；
顶栏左上角的按钮或栏底箭头可以整体收起，收起后内容区自动补满；窄屏下改为浮层加遮罩。

页面标题放在顶栏正中。数据大屏那条是行楷题字（`华文行楷`，回退新魏/楷体），`BN` 单独留在等宽字体里当标识；
其余页面沿用普通字重的小号标题。手机宽度下标题只留「结构健康监测」主名。

不按项目切分数据：顶栏没有项目下拉，大屏与各列表一律统计全部项目（常泰长江大桥、沪苏通大桥…）的记录，
项目列表只在设备表单的「所属项目」里出现。

- 数据大屏：6 张 KPI（数字滚动）、报警趋势面积图、类型分布环形图、中国省界地图（真实省界路径 + 经纬度墨卡托投影打点）、在线率环形、级别分布条形、实时数据流、最新告警、视频缩略、SSE 断线退避重连
- 设备管理：统计条、四条件筛选、9 列表格、抽屉式编辑（14 个字段）、远程配置下发对话框
- 传感器管理：卡片网格（迷你曲线取真实 24h 数据）、抽屉详情（字段表 + 曲线）、分组管理
- 数据管理：传感器 / 对比传感器 / 字段 / 时间 / 粒度、多序列折线（缺值断段）、报表任务轮询与下载
- 视频监控：通道网格、FLV 播放、通道心跳
- 告警管理：统计条、筛选、确认 / 关闭、报警配置、生效规则

配色、间距、圆角、动效全部来自设计稿的 `oklch` 令牌（`web/src/styles/tokens.css`），明暗主题跟随顶栏按钮，`localStorage` 记住。

卡片统一走 `--card-shadow`（贴地阴影 + 扩散阴影）与 `--card-edge`（顶边高光）两组令牌，
浅色靠投影、深色靠高光把卡片从底面上托起来；面板、KPI、传感器卡、视频卡共用同一套边界处理。
折线图的纵轴按网格线标出 4 到 5 个刻度值，小数位随量级收缩；地图上的点位会遮住所在省份的名字，
该省名自动隐去，不会出现两层文字叠字。

## 已实现

- 大屏统计口径：设备/传感器/报警（未关闭）/视频通道，加上今日数据量（走 `sys.partitions` 分区元数据，不扫表）与低电量设备
- 设备台账、编辑、远程配置下发、跳转该设备传感器
- 传感器卡片、分组、最新值、24 小时曲线、字段明细
- 数据对比（时间轴对齐、缺值留空）、CSV 报表异步导出（监测数据明细 / 报警记录 / 设备台账三类，7 天后过期）
- 视频通道与播放地址（云台控制后端只有空壳，界面不提供）
- 告警列表、确认、关闭、配置增删改、按级别统计
- BN 云平台推送接收端：动态字段解析、MD5 幂等、暂存表 + `MERGE` 批量落库、实时报警判定

## 与《技术路线》的差异（本机可直接跑）

| 项 | 文档 | 本实现 | 原因 |
|---|---|---|---|
| 缓存/幂等/报警窗口 | Redis 7 | Caffeine 进程内 | 本机没有 Redis，`LocalCache` 是唯一替换点，接口不变 |
| 批量写入 | `SqlBulkCopy` | 暂存表 + `batchUpdate` + `MERGE` | 不用实现 `ISQLServerBulkRecord`，量级够用 |
| 密码 | BCrypt | MD5 | 不引 spring-security-crypto，演示用；上生产换 BCrypt |
| 分区维护 | 定时任务 | 手工脚本 | 演示量不需要，脚本留在 `db\` |

## 联调：模拟设备推送

```bash
curl -X POST http://localhost:8090/open/bn/push \
  -H "Content-Type: application/json" \
  --data-binary "@db/sample-push.json"
```

实测：第一次 `{"accepted":1}`，第二次 `{"accepted":0}`（幂等键命中），`sensor_reading` 不多出行。

## 验证

界面按 9 档视口逐页核对，横向一律无溢出（`documentElement.scrollWidth == innerWidth`），浏览器控制台无异常，后端日志无 ERROR：

| 视口 | 表现 |
| --- | --- |
| 360×800 / 390×844 / 430×932 | 单列卡片，左侧按钮栏浮在内容之上、带遮罩，点空白或底部箭头收起；顶栏标题只留主名 |
| 600×960 / 820×1180 | 传感器卡片 3 列，表格在容器内横向滚动 |
| 1024×768 | 三栏大屏回落成单列，纵向滚动 |
| 1366×768 | 三栏大屏，内容高于视口时自然滚动 |
| 1440×900 / 1600×900 / 1920×1080 | 大屏整屏铺满，图表按剩余高度等比压缩，无纵向滚动条 |

设备连续 15 分钟没有上报会被离线任务置为离线（阈值 = `collect_period × 3`），
演示时推一条报文即可恢复在线：

```bash
curl -X POST http://localhost:8090/open/bn/push -H "Content-Type: application/json" --data-binary "@db/sample-push.json"
```

`sensordata` 的键必须能在 `sensor_field.field_key` 里找到；找不到就记 `parsed=0`，整条原始报文留在 `sensor_reading_raw`，不报错、不丢数据。

## 下一步

报表导出一律写 CSV（UTF-8 BOM，Excel 直接打开不乱码），按 `biz_type` 分成监测数据明细 / 报警记录 / 设备台账三套查询。要 xlsx 就接 EasyExcel 的 `EasyExcel.write(file, Row.class).sheet().doWrite(list)`，任务状态机与下载接口不用改。

云台控制、设备回执、规则类型 4–7（变化率 / 离线 / 低电量 / SIM 到期）在后端还没有真正的执行链路，界面上不做入口，等接上流媒体服务与设备回执再补。
