# VenuePass 演示前端

Next.js 单页演示界面，直接调用本仓库的 Spring Boot API。页面提供普通用户注册/登录、活动与场次浏览、普通及热门预约、排队结果查询、我的预约、运营创建场馆/活动/场次、支付与退款登记、场次取消、报表和入场核销。

在仓库根目录运行 `docker compose up --build`，打开 <http://localhost:3000>。也可以先启动后端，再在 `frontend/` 运行 `npm ci`、`npm run dev`。此时 Next.js 默认把请求代理到 `http://localhost:8080`；后端不在此地址时，设置 `BACKEND_URL` 环境变量。

运营账号为 `operator`，核销账号为 `checker`，密码由后端 `OPERATOR_PASSWORD` 和 `CHECKER_PASSWORD` 决定。普通用户可在首页注册。演示登录信息仅留在页面内存，刷新页面需重新登录。前端的支付/退款按钮只登记外部已确认的交易，不执行实际扣款或退款。

场次创建表单使用 UTC 时间，与 Compose 容器中的后端时间一致。若独立运行后端且服务器时区不是 UTC，填写场次时间时应按后端服务器时间调整。
