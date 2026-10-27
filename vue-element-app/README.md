# 心理健康 AI 助手

> 把心事说给懂你的 AI 听。Vue 3 + TypeScript + Element Plus 实现的前台倾诉 / 情绪打卡 / 心理科普与运营后台演示。

基于 **Vite + Vue 3 + TypeScript + Element Plus** 的前后台一体化项目：前台面向来访者（倾诉、阅读、情绪打卡），后台面向运营与咨询师（数据分析、用户管理、知识文章、查询记录、情绪日志）。

所有页面均已接入 [`../spring-ai-rag`](../spring-ai-rag) 后端：登录鉴权使用 JWT + Redis，文章、情绪、咨询、用户与仪表盘数据全部来自真实接口，不再使用 mock 数据。

界面视觉、组件与动效均为手写实现，除 Element Plus 外未引入其他 UI 框架或图表库（图表是自绘 SVG / CSS）。源码全量 TypeScript，`npm run build` 会先跑类型检查。

## 环境要求

- Node.js **24.2.0**（项目已通过 `.nvmrc` 锁定）
- npm

```bash
nvm use        # 自动读取 .nvmrc，切换到 Node 24.2.0
```

## 快速开始

前端的数据与登录都依赖后端，请先启动 Redis 与 `../spring-ai-rag`：

```powershell
# 1) Redis（登录会话）
docker compose -f ../spring-ai-rag/docker-compose.yml up -d redis

# 2) 后端（demo：H2 + 内存向量库，默认 http://localhost:8080）
$env:REDIS_PORT = "16379"   # 用 compose 的 Redis；本机 6379 则删掉这行
mvn -f ../spring-ai-rag/pom.xml spring-boot:run "-Dspring-boot.run.profiles=demo"

# 3) 前端
npm install
npm run dev
```

- 前台：http://localhost:5173/front （账号 `front` / `front`）
- 后台：http://localhost:5173/back （账号 `back` / `back`）
- 未登录会先跳到 `/login`，登录页表单上方可直接点选两个演示身份。
- 也可以直接用本机 Redis；后端完整启动方式见 `../spring-ai-rag/README.md` 第 4.0 / 5 节。

## 对接后端（Spring AI RAG）

前后台所有页面均已接入真实后端，接口封装在 `src/api/`：

| 页面 | 接口模块 | 说明 |
| --- | --- | --- |
| 登录 / 退出 | `api/auth.ts` | JWT 登录、当前用户、注销；令牌存在 `localStorage` 或 `sessionStorage` |
| 前台首页 | `api/articles.ts`、`api/moods.ts` | 已发布文章、我的情绪记录 |
| 前台 · AI 咨询 | `api/chat.ts` | SSE 流式输出、知识库引用、对话记录、重新生成、删除、导出 Markdown |
| 前台 · 心理科普 | `api/articles.ts` | 分类 / 关键词 / 排序、详情（阅读量 +1） |
| 前台 · 情绪日记 | `api/moods.ts` | 按天打卡（同一天覆盖）、删除 |
| 个人资料（前台 / 后台） | `api/profile.ts` | 查看资料与统计、修改姓名/身份、修改密码 |
| 后台 · 数据分析 | `api/dashboard.ts` | 指标、趋势、在线人数等真实聚合数据 |
| 后台 · 用户管理 | `api/users.ts` | 搜索 / 角色筛选、新建、编辑、启停用、删除、密码重置 |
| 后台 · 知识文章 | `api/articles.ts` | 全部文章（含草稿）、新建 / 编辑 / 上下线 / 删除 |
| 后台 · 知识库 | `api/knowledge.ts` | 文档列表 / 新建 / 上传 `.md` / 查看原文 / 删除 |
| 后台 · 查询记录 | `api/consultations.ts` | 会话列表、详情、标记已跟进 |
| 后台 · 情绪日志 | `api/moods.ts` | 全部用户情绪记录、删除 |

- 开发时 Vite 把 `/api` 代理到 `http://127.0.0.1:8080`（见 `vite.config.ts`），不需要处理跨域。
- 请求统一走 `api/http.ts`：自动附加 `Authorization: Bearer <token>`；401 时清理登录态并跳回 `/login?redirect=原地址`；502/503/504 提示「后端服务不可用」。
- 错误响应按 Spring `ProblemDetail` 解析 `detail` 字段，直接展示可读原因（如「账号或密码不正确」「账号已停用」）。
- 后端 `/api/admin/**` 统一要求 `back` 角色；前台业务接口由前端登录守卫保护（其中文章与 RAG 聊天接口在后端仍为公开）。跨角色访问后台接口返回 403，由页面提示无权限。
- 后端未启动时，页面会提示「无法连接后端服务」，不会白屏。
- 部署到其它地址时设置 `VITE_API_BASE=https://api.example.com` 后重新构建。
- 后端启动方式与完整接口清单见 `../spring-ai-rag/README.md` 第 5、7 节。

## 常用脚本

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动开发服务器 |
| `npm run type-check` | 只做类型检查（`vue-tsc --noEmit`，含 `.vue` 模板） |
| `npm run build` | 先类型检查，再构建生产包到 `dist/` |
| `npm run preview` | 本地预览生产构建 |

## 页面与路由

前台与后台通过路径前缀区分，各自使用独立布局。

| 路径 | 页面 | 布局 | 账号 |
| --- | --- | --- | --- |
| `/front` | 首页（品牌介绍、能力、最新文章与情绪概览） | `FrontLayout` | front |
| `/front/chat` | AI 咨询（SSE 流式对话、知识库引用、历史记录） | `FrontLayout` | front |
| `/front/articles` | 心理科普（分类、搜索、详情弹窗） | `FrontLayout` | front |
| `/front/mood` | 情绪日记（五级打卡、标签、7 日趋势） | `FrontLayout` | front |
| `/front/lab` | 性能实验室（批量 DOM / 合成层动画 / 布局读取三项对照实验） | `FrontLayout` | front |
| `/front/profile` | 个人资料（资料与统计、修改姓名/身份、修改密码） | `FrontLayout` | front |
| `/login` | 前台 / 后台登录（演示账号快捷填入、记住我、回原页面） | 独立布局 | — |
| `/back/dashboard` | 数据分析（总用户/情绪日志/咨询会话/在线人数、三张趋势图） | `BackLayout` | back |
| `/back/users` | 用户管理（搜索、角色筛选、新建/编辑、启停用、删除、重置密码） | `BackLayout` | back |
| `/back/articles` | 知识文章管理（筛选、新建/编辑、发布、删除） | `BackLayout` | back |
| `/back/knowledge` | RAG 知识库（文档列表、新建/上传、删除、查看原文） | `BackLayout` | back |
| `/back/consultations` | 查询记录（筛选会话、详情抽屉、标记跟进） | `BackLayout` | back |
| `/back/mood-logs` | 情绪日志（概览、等级筛选、打卡明细） | `BackLayout` | back |
| `/back/profile` | 个人资料（与前台共用同一个页面组件） | `BackLayout` | back |

- `/` 重定向到 `/front`；未匹配路由回到 `/front`。
- 前台与后台都需要登录：未登录时守卫跳到 `/login?redirect=原地址`，登录后自动回去。
- 账号带角色：`front` 只能进前台，`back` 只能进后台；走错入口会被送回本角色首页，已登录访问 `/login` 同样回首页。
- 后台侧栏可收起（236px ↔ 76px，状态记在 `localStorage`），窄屏（< 1024px）自动换成抽屉菜单。
- `meta.title` 由 `router.afterEach` 写入 `document.title`（形如 `数据分析 · 心理健康 AI 助手`）。

## 登录与权限

登录由后端校验账号密码并签发 JWT，前端只负责保存令牌与用户信息。登录页表单上方可一键填入两个主账号：

| 入口 | 账号 | 密码 | 登录后落点 | 身份 |
| --- | --- | --- | --- | --- |
| 前台（来访者） | `front` | `front` | `/front` | 小满 · 来访者 |
| 后台（运营） | `back` | `back` | `/back/dashboard` | 林医生 · 咨询师/管理员 |

后端首次启动还会写入 `chenyu / demo123`（前台）、`sunlan / demo123`（后台）与 `zhouyu / demo123`（前台，已停用，用于验证停用提示）。

- 前后台都要登录：`/front/*` 与 `/back/*` 的路由 meta 都是 `requiresAuth: true`，并各自带 `role: 'front' | 'back'`。
- 登录态在 `src/composables/useAuth.ts`：模块级 `reactive` 单例 + `computed`，守卫与页面读同一份数据，无需额外状态库。
- 令牌保存在 `xinli.auth.token`，用户信息保存在 `xinli.auth.user`；勾选「记住我」存 `localStorage`（关浏览器仍在），否则只存 `sessionStorage`。
- 登录失败直接展示后端返回的原因（如「账号或密码不正确」「账号已停用，请联系管理员」），不再在前端硬编码账号。
- `refresh()` 会调用 `/api/auth/me` 校验令牌并刷新用户资料；令牌失效时自动清空登录态。
- 守卫逻辑在 `src/router/index.ts` 的 `beforeEach`：未登录 → `/login?redirect=原地址`；角色不符 → 本角色首页；已登录访问 `/login` → 本角色首页。
- 401 统一处理在 `src/api/http.ts`：清理令牌与用户信息并跳回登录页；403 表示角色不符，页面提示无权限；502/503/504 只提示后端不可用，不清登录态。
- 退出：前台点右上角头像 → 轻量下拉菜单里的「退出登录」；后台点右上角头像，`el-dropdown` 菜单里有「退出登录」，二次确认后回登录页。退出会调用 `/api/auth/logout` 注销 Redis 会话。
- 修改密码在个人资料页完成：后端校验当前密码，成功后注销其他设备的会话，当前设备保持登录。
- 后台用户管理里停用账号、修改角色、重置密码，都会立即注销目标用户的全部会话。

## 目录结构

```
src/
├── api/                   # 后端接口封装：http / auth / articles / moods / chat / knowledge / consultations / dashboard / users / profile
├── layouts/
│   ├── FrontLayout.vue    # 前台：吸顶导航 + 账号/退出 + 页脚（含心理援助热线）
│   └── BackLayout.vue     # 后台：可收起侧栏 + 顶栏（头像菜单退出）+ 移动端抽屉
├── views/
│   ├── auth/LoginView.vue # 前台 / 后台统一登录（身份快捷填入 + 记住我）
│   ├── front/             # HomeView / ChatView / ArticlesView / MoodView
│   ├── back/              # DashboardView / UsersView / ArticlesView / KnowledgeView / ConsultationsView / MoodLogsView
│   └── shared/ProfileView.vue # 个人资料（前后台共用，按路由判断布局）
├── components/
│   ├── AppIcon.vue        # 内联 SVG 图标集
│   ├── BrandLogo.vue      # 品牌标识（浅色 / 深色）
│   ├── PageHeader.vue     # 后台页头（标题 + 描述 + 操作区）
│   ├── PanelCard.vue      # 后台通用面板（标题 / 描述 / 右侧操作）
│   ├── SearchField.vue    # 防抖搜索框（默认 300ms）
│   ├── MoodDots.vue       # 5 级情绪点阵
│   ├── AdminNav.vue       # 后台菜单（侧栏与抽屉共用，支持折叠）
│   ├── SiteFooter.vue     # 前台页脚（安全提示 + 热线）
│   ├── StatCard.vue       # 指标卡（数值 + 环比 + 迷你折线）
│   ├── UserBadge.vue      # 用户名首字头像
│   ├── Sparkline.vue      # 迷你趋势线
│   ├── AreaChart.vue      # 面积图（SVG，含描边动画）
│   ├── BarChart.vue       # 横向条形图
│   └── DonutChart.vue     # 环形图（conic-gradient + 图例）
├── composables/
│   ├── useActionGuard.ts  # 按钮防连点 + loading（内部复用 debounce/throttle）
│   └── useAuth.ts         # 登录态单例（front / back 双账号 + 角色 + 存储）
├── data/
│   ├── catalog.ts         # 固定目录：文章分类、情绪标签与五级选项（非业务数据）
│   └── nav.ts             # 前后台导航配置（路径 / 路由名 / 图标 / 顶栏描述）
├── directives/reveal.ts   # v-reveal：进入视口时揭示
├── utils/
│   ├── labels.ts          # 状态/风险/情绪等级映射，避免重复字典
│   └── timing.ts          # debounce / throttle 最小实现（无 lodash）
├── types/
│   ├── index.ts           # 全站数据模型与联合类型
│   └── router.d.ts        # RouteMeta 类型增强
├── styles/
│   ├── _variables.scss    # 设计令牌
│   ├── _mixins.scss       # 混入
│   └── index.scss         # 全局基础样式 + Element Plus 变量覆盖
├── router/
│   ├── index.ts           # 路由表（前台布局静态、后台布局懒加载）
│   └── prefetch.ts        # 空闲 / 悬停预取页面 chunk
├── App.vue                # 仅 <router-view />
├── main.ts                # 注册路由与 v-reveal
└── env.d.ts               # vite/client 类型引用
```

根目录：`tsconfig.json`（严格模式 + `@/*` 路径）、`vite.config.ts`、自动生成的 `auto-imports.d.ts` / `components.d.ts`（勿手改）。

## TypeScript 规范

`tsconfig.json` 开启 `strict`、`noUnusedLocals`、`noUnusedParameters`、`noImplicitOverride`、`verbatimModuleSyntax`，用 `vue-tsc` 连模板一起检查。

- **类型集中**：数据模型与联合类型放 `src/types/index.ts`，页面与组件只消费，不各自重复声明；路由 `meta` 通过 `src/types/router.d.ts` 的 module augmentation 补类型。
- **组件 props**：`<script setup lang="ts">` + 泛型 `defineProps<Props>()`；有默认值时用 `withDefaults(defineProps<Props>(), { ... })`，不要再用运行时对象写法。
- **只导类型用 `import type`**：`verbatimModuleSyntax` 下漏写 `type` 会直接报错，这是刻意的（利于 Tree-shaking 与可擦除语法）。
- **不用 `any`**：外部数据先声明 `unknown` 再用类型守卫/断言收窄；确需断言时写清原因，禁用 `@ts-ignore`，必要时用带说明的 `@ts-expect-error`。
- **字符串魔法值用联合类型**：如 `ArticleStatus`、`RiskLevel`、`MoodLevel`，映射表用 `Record<Union, X>`，避免拼写错误到运行期才暴露。
- **Element Plus 表格插槽**：`#default="{ row }"` 的 `row` 是 `DefaultRow`，调用带类型的处理函数时在模板里显式断言，如 `@click="openEdit(row as Article)"`。
- **生成物不手改**：`auto-imports.d.ts`、`components.d.ts` 由 unplugin 在 dev/build 时生成，删掉后重启 dev 即可重建。
- **验证**：提交前跑 `npm run type-check`；`npm run build` 已内置类型检查，类型错会直接阻断构建。

## 组件复用与交互防护

- **先找现成组件**：后台列表页统一用 `PanelCard` + `SearchField` + `PageHeader`，菜单统一由 `AdminNav` 渲染（侧栏与抽屉共用 `data/nav.ts`），页脚是 `SiteFooter`；新增页面不要再复制这些结构。
- **按钮防连点**：所有会改数据、弹确认框或触发导出的按钮都用 `useActionGuard` 包裹，返回的 `pending` 直接绑 `:loading`。

| 位置 | 动作 | 策略 |
| --- | --- | --- |
| 后台 · 知识文章 | 保存 / 上下线 / 删除 | 600 / 400 / 500ms 节流 |
| 后台 · 查询记录 | 导出 / 标记已跟进 | 900 / 600ms 节流 |
| 后台 · 数据分析、情绪日志 | 导出 | 800 / 900ms 节流 |
| 前台 · 情绪日志 | 记录今天 | 800ms 节流（避免写入两条） |
| 前台 · AI 咨询 | 发送 | 300ms 节流（连按回车不刷屏） |
| 搜索框 | 关键词过滤 | `SearchField` 300ms 防抖 |

- **实现**：`utils/timing.ts` 提供 `debounce` / `throttle`；`useActionGuard` 在其上包一层异步互斥（动作未结束不重复进入）并返回 `pending`。组件卸载会取消未执行的防抖（`SearchField` 中 `onBeforeUnmount(flush.cancel)`）。
- **注释**：新组件、组合式函数、工具函数都有文件头注释；不直观的时序与性能处理（为什么节流、为什么懒加载）就地注释。

## 性能与首屏优化

- **布局按需**：前台布局静态（首屏必需），`/back` 布局改为 `() => import('@/layouts/BackLayout.vue')`，后台的 `el-menu` / `el-drawer` / `el-table` 不再进入首屏。
- **chunk 拆分**：`vite.config.ts` 只把 `vue`、`vue-router` 拆成独立 vendor chunk，组件库交给 Rollup 按路由拆分；首屏入口只预加载入口、Vue / Router vendor、请求封装与全局样式。
- **预取**：`router/prefetch.ts` 在首屏 `requestIdleCallback` 空闲后预取前台其余三个页面；导航链接 `mouseenter` / `focus` 时也会预取，点击即命中缓存。
- **渲染**：首屏以下的 `.strip` / `.section` 与页脚加 `content-visibility: auto`（`.cv-auto`），跳过屏幕外布局，滚动到附近再渲染。
- **构建**：`modulePreload.polyfill: false`（现代浏览器不需要）、`target: es2020`、`reportCompressedSize: false`。实测首屏 6 个文件 ≈ **262 KB（gzip ≈ 86 KB）**，后台页面各自 5–36 KB 按需加载。
- **交互组件按需**：确认框（`ElMessageBox`）会连带 dialog / overlay / focus-trap 样式，只放在懒加载的后台布局里；前台的账号下拉是自绘的轻量菜单（约 30 行 CSS/逻辑），避免把 `el-dropdown` 的 popper 依赖打进首屏——实测若改用 `el-dropdown`，入口会从 33 KB 涨到 97 KB。
- **三条渲染原则**：批量 DOM 先在游离节点 / `DocumentFragment` 上改完再一次挂载；动画只写 `transform` / `opacity`；`getBoundingClientRect` 等布局读取只在进入、滚动、缩放时做，其余时间用缓存。`/front/lab` 把这三条做成可对照的实验页。
- **细节落地**：`v-spotlight` / `v-tilt` 在 `pointerenter` 时缓存 rect 并按帧合并样式写入；`v-parallax` 先读完所有 rect 再统一写变量；`AreaChart` 悬停读数与补间动画分别做了 rect 缓存和 ~30fps 提交；`ChatView` 的流式滚动把一个 token 一次的 `nextTick + scrollHeight` 合并成每帧一次。

## 设计系统

调性：古典洛丽塔星夜。象牙纸底 + 酒红 + 香槟金 + 灰粉，标题衬线、正文无衬线。日间像一本烫金边的旧诗集，夜间切到午夜墨蓝的星图；安静、可信，又保留一点舞台感，而不是通用 SaaS 后台。

前台与登录页共用 `AuroraBackdrop.vue`：三层速度不同的 CSS 星场、缓慢呼吸的星云与银河、月牙与两条旋向相反的星轨、绕月巡游的星点、旋转的月华涡流与放射金线、错开半拍扩散的月晕涟漪、爱心星座、四颗流星、八枚四角星闪光、飘落的缎带花瓣、漂浮珍珠、上下与左右蕾丝边、两只缎带蝴蝶结和纸面颗粒。所有动效只写 `transform` / `opacity`，`prefers-reduced-motion` 下退化为静态星图；窄屏与 `quiet` 模式会自动关掉最闹的一批。

### 颜色

| 用途 | 令牌 | 色值 |
| --- | --- | --- |
| 品牌主色（酒红） | `$color-primary` | `#75424f` |
| 主色深（主按钮/悬停） | `$color-primary-strong` | `#552b37` |
| 主色浅底（标签/高亮） | `$color-primary-soft` | `#f4e6e9` |
| 香槟金（提示/次强调） | `$color-accent` | `#b5884d` |
| 灰粉（情绪低落色） | `$color-lilac` | `#b78490` |
| 危险/下降 | `$color-danger` | `#a84c3b` |
| 正文 | `$color-text-regular` | `#4f4146` |
| 次要文字 | `$color-text-secondary` | `#7d6c72` |
| 边框 | `$color-line` | `#e6dccb` |
| 页面背景 | `$color-bg` | `#f6eee2` |
| 卡片表面 | `$color-surface` | `#fffbf3` |

### 字体、圆角、阴影

- 标题衬线：`$font-display` = Noto Serif SC → Source Han Serif SC → Songti SC → STSong，用于 h1/h2、区块标题、指标数值。
- 正文无衬线：Inter → PingFang SC / Microsoft YaHei，14px / 1.7。
- 字号：首页 hero `clamp(38px, 4.8vw, 58px)`、区块标题 `clamp(26px, 3vw, 40px)`、登录标题 40px、后台页标题 28px。
- 圆角：卡片 18px、大容器 24–32px、控件 12px、标签胶囊 999px。
- 阴影克制：卡片默认 `$shadow-sm`，悬停/浮层用 `$shadow-md` / `$shadow-lg`，只用偏暖的中性色，不用纯黑阴影。

### 间距与断点

- 间距令牌 `$space-1` ~ `$space-8`（4/8/12/16/24/32/40/64）。
- 前台容器 1160px，后台内容 1320px，后台侧栏 236px（收起 76px）。
- 断点：`$bp-sm` 640、`$bp-md` 768、`$bp-lg` 1024。后台在 1024px 以下收起侧栏，改为抽屉；前台在 640px 以下把导航移到第二行横向滑动。

### 动效

| 动效 | 实现 | 说明 |
| --- | --- | --- |
| 滚动揭示 | `v-reveal` / `v-reveal="{ delay: 120 }"` | IntersectionObserver 加 `.is-visible`，位移 16px + 渐显 |
| 路由切换 | `<Transition name="page">` | 前台与后台的 `router-view` 都已包裹 |
| 图表入场 | `AreaChart` / `BarChart` | 折线描边（`@keyframes draw`）、条形从左展开 |
| 悬停反馈 | 卡片 `translateY(-2~4px)` + 阴影加深 | 时长 `$duration-base`，曲线 `$ease-out` |
| 入场节奏 | `@keyframes enter-rise` | 登录页左右栏 0–280ms 递进，只做 10px 位移、不改透明度 |
| 浮动装饰 | `@keyframes float` | 首页消息卡片与浮层标签，6–7s 循环 |
| 星夜背景 | `AuroraBackdrop.vue` | 星场漂移/闪烁、星云呼吸、月轨与月华涡流旋转、月晕涟漪、流星、花瓣飘落与闪光；只动 `transform` / `opacity` |

`prefers-reduced-motion: reduce` 时全局动效降到 0.01ms、延迟清零，且 `.reveal` 直接可见。

### 图表

不引入 ECharts，全部自绘：

- `AreaChart`：600×200 `viewBox` + `preserveAspectRatio="none"`，渐变填充由唯一 `id` 避免冲突。
- `DonutChart`：`conic-gradient` 按百分比分段，中心留白放总计。
- `BarChart`：HTML 轨道 + `scaleX` 动画，宽度按最大值归一。
- `Sparkline`：指标卡内的 7 点迷你折线。

## 数据与交互说明

- 业务数据全部来自后端接口（见上方「对接后端」映射），`src/data/` 只保留文章分类、情绪标签与五级选项等固定目录。
- 登录、个人资料、文章、情绪记录、咨询记录、用户管理、知识库均为真实读写；刷新页面数据依然存在（demo profile 重启后端后会重置为示例数据）。
- AI 咨询的回复由后端 RAG 链路生成，历史记录保存在后端；没有配置模型 token 时，页面会收到后端返回的错误提示。
- 后台用户管理带自我保护规则：不能停用 / 删除自己、不能移除自己的后台角色；停用、改角色、重置密码会让目标用户的旧令牌立即失效。
- 修改密码：当前设备保持登录，其他设备被注销；当前密码错误按普通表单错误提示，不会跳回登录页。
- 空状态用 `el-empty`；破坏性操作用 `ElMessageBox.confirm`；成功/提示用 `ElMessage`；请求失败优先展示后端的 `detail` 文案。

## 样式与 SCSS

已接入 Dart Sass，`<style lang="scss">` 与 `.scss` 文件开箱即用：

```vue
<style scoped lang="scss">
.panel {
  padding: $space-5;
  border-radius: $radius-card;

  &__title {
    @include truncate;
  }

  @include media-down($bp-md) {
    padding: $space-3;
  }
}
</style>
```

- 设计令牌在 `src/styles/_variables.scss`（`$color-*`、`$space-*`、`$radius-*`、`$shadow-*`、`$bp-*`）。
- 混入在 `src/styles/_mixins.scss`：`media-down`、`media-up`、`truncate`、`line-clamp`、`flex-center`、`focus-ring`。
- `vite.config.ts` 的 `css.preprocessorOptions.scss.additionalData` 会把这两个文件自动注入每个样式块，组件里**不用**再写 `@use`（这两个文件自身除外）。
- 统一用 `@use` / `@forward`，不要用已废弃的 `@import`；嵌套建议不超过 3 层。
- 全局基础样式在 `src/styles/index.scss`，由 `src/main.ts` 引入。

> 编辑器（Volar）可能提示 `$color-primary` 未定义，因为自动注入只发生在构建期；运行时正常，可忽略。

## Element Plus

已通过 `unplugin-auto-import` + `unplugin-vue-components` 开启按需自动引入：

- 模板中直接使用 `<el-button />`、`<el-table />` 等组件；
- 脚本中直接调用 `ElMessage`、`ElMessageBox` 等 API；
- 组件样式按需加载，无需手动引入。

主题覆盖统一写在 `src/styles/index.scss` 的 `html:root` 中（`--el-color-primary` 及其 light/dark 阶梯、边框、填充、圆角、字体），比 `:root` 优先级高，不会被按需样式覆盖。

图标没有使用 `@element-plus/icons-vue`，而是 `AppIcon.vue` 的内联 SVG（线条风格，随字色变化），避免额外依赖。

`@` 别名已指向 `src/`。

## Agent Skills（项目技能）

`.dsh/skills/` 下内置 6 个项目级技能，DSH 在本目录工作时会自动发现（无需重启），在输入框输入 `/` 即可调用：

| 技能 | 用途 |
| --- | --- |
| `ui-design-system` | 心理健康 AI 助手的品牌调性、设计令牌、组件清单、动效与前后台页面蓝本 |
| `frontend-ui-craft` | 间距节奏、状态完备、响应式、可访问性与中文文案 |
| `vue-component` | Vue 3 单文件组件写法、TS props、目录约定、路由与布局 |
| `typescript-standards` | 严格模式、类型组织、`import type`、表格插槽断言与类型检查流程 |
| `element-plus` | 按需引入、表单校验、表格、消息/确认框、主题变量 |
| `vite-vue-build` | dev/build/preview/type-check、别名、构建报错与白屏排查 |

新增技能：在 `.dsh/skills/<name>/SKILL.md` 写 YAML frontmatter（必填 `name`、`description`，可选 `whenToUse`、`disable-model-invocation`、`user-invocable`），`name` 用 kebab-case。想让所有项目都可用，把技能目录复制到 `%DSH_HOME%\skills\`（本机为 `C:\Users\Aurora\AppData\Roaming\DeepSeek Harness\dsh-home\skills`）。

## 安全与合规

- 全站文案明确「AI 不能替代专业诊断与治疗」，页脚与对话页常驻 **心理援助热线 12356**（紧急情况 120 / 110）。
- 不使用「治愈」「确诊」「保证好转」等医疗承诺性表述。
- 演示数据均为虚构用户，不包含真实个人信息。
- 登录由后端校验并签发 JWT，Redis 保存会话；注销、停用、改角色、改密都会让旧令牌立即失效。
- 令牌目前存在 `localStorage` / `sessionStorage`，前端可读，存在 XSS 风险；生产环境建议改用 HttpOnly Cookie。
- 演示账号与示例密码仅用于练手；后端当前为演示方便以明文保存密码，上线前必须改为 BCrypt 等哈希存储，并强制修改所有示例密码。
