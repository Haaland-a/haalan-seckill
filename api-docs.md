# Haalan 电商平台 API 接口文档

> 基础地址：`http://localhost:8080`（网关入口）
>
> 统一响应格式：
> ```json
> {
>   "code": 200,        // 状态码: 200-成功, 500-服务器错误, 其他-业务错误
>   "message": "success", // 提示信息
>   "data": {},         // 响应数据
>   "timestamp": 1234567890 // 时间戳
> }
> ```
>
> 认证方式：请求头携带 JWT Token（`authorization: Bearer <token>`），登录后获取

---

## 目录

1. [用户服务（user-service）](#1-用户服务user-service)
2. [商品服务（item-service）](#2-商品服务item-service)
3. [订单服务（order-service）](#3-订单服务order-service)
4. [秒杀服务（seckill-service）](#4-秒杀服务seckill-service)
5. [搜索服务（search-service）](#5-搜索服务search-service)
6. [内部 Feign 接口](#6-内部-feign-接口)

---

## 1. 用户服务（user-service）

### 1.1 用户注册

- **接口说明**：用户注册
- **URL**：`POST /api/user/register`
- **是否需要登录**：否

**请求参数（JSON Body）：**

| 参数名      | 类型     | 必填 | 说明  |
|----------|--------|----|-----|
| username | String | 是  | 用户名 |
| password | String | 是  | 密码  |
| phone    | String | 否  | 手机号 |
| email    | String | 否  | 邮箱  |

**响应数据：** `R<TUserVO>`

| 字段       | 类型     | 说明   |
|----------|--------|------|
| id       | Long   | 用户ID |
| username | String | 用户名  |
| phone    | String | 手机号  |
| email    | String | 邮箱   |

---

### 1.2 用户登录

- **接口说明**：用户登录，获取 JWT Token
- **URL**：`POST /api/user/login`
- **是否需要登录**：否

**请求参数（JSON Body）：**

| 参数名      | 类型     | 必填 | 说明  |
|----------|--------|----|-----|
| username | String | 是  | 用户名 |
| password | String | 是  | 密码  |

**响应数据：** `R<LoginVO>`

| 字段       | 类型     | 说明        |
|----------|--------|-----------|
| token    | String | JWT Token |
| userId   | Long   | 用户ID      |
| username | String | 用户名       |

---

### 1.3 获取用户信息

- **接口说明**：获取当前登录用户信息
- **URL**：`GET /api/user/info`
- **是否需要登录**：是

**请求参数：** 无（从 Token 获取用户ID）

**响应数据：** `R<UserInfoVO>`

| 字段       | 类型            | 说明    |
|----------|---------------|-------|
| userId   | Long          | 用户ID  |
| username | String        | 用户名   |
| phone    | String        | 手机号   |
| email    | String        | 邮箱    |
| avatar   | String        | 头像URL |
| nickName | String        | 昵称    |
| gender   | Integer       | 性别    |
| birthday | LocalDateTime | 生日    |

---

### 1.4 修改用户信息

- **接口说明**：修改当前登录用户信息
- **URL**：`PUT /api/user/info`
- **是否需要登录**：是

**请求参数（JSON Body）：** `UserInfoVO`

| 参数名      | 类型            | 必填 | 说明  |
|----------|---------------|----|-----|
| nickName | String        | 否  | 昵称  |
| phone    | String        | 否  | 手机号 |
| email    | String        | 否  | 邮箱  |
| gender   | Integer       | 否  | 性别  |
| birthday | LocalDateTime | 否  | 生日  |

---

### 1.5 获取 OSS 上传凭证

- **接口说明**：获取阿里云 OSS 前端直传凭证（上传头像用）
- **URL**：`GET /api/user/oss/credential`
- **是否需要登录**：是

**请求参数：** 无

**响应数据：** `R<OssUploadCredentialVO>`

| 字段              | 类型     | 说明                  |
|-----------------|--------|---------------------|
| accessKeyId     | String | 临时 AccessKey ID     |
| accessKeySecret | String | 临时 AccessKey Secret |
| securityToken   | String | 安全令牌（STS Token）     |
| endpoint        | String | OSS 访问域名            |
| bucketName      | String | Bucket 名称           |
| filePrefix      | String | 文件存储路径前缀（avatar）    |
| expiration      | Long   | 凭证过期时间（秒）           |

**限制规则：**

- 头像每天只能上传一次
- 获取凭证频率限制：每小时一次

---

### 1.6 退出登录

- **接口说明**：退出登录，清除 Token
- **URL**：`POST /api/user/logout`
- **是否需要登录**：是

**请求参数：** 无

**响应数据：** `R<Void>`

---

### 1.7 修改密码

- **接口说明**：修改当前用户密码
- **URL**：`POST /api/user/change-password`
- **是否需要登录**：是

**请求参数（JSON Body）：**

| 参数名             | 类型     | 必填 | 说明    |
|-----------------|--------|----|-------|
| oldPassword     | String | 是  | 旧密码   |
| newPassword     | String | 是  | 新密码   |
| confirmPassword | String | 是  | 确认新密码 |

**响应数据：** `R<Void>`

---

### 1.8 添加收货地址

- **接口说明**：新增用户收货地址
- **URL**：`POST /api/user/address`
- **是否需要登录**：是

**请求参数（JSON Body）：**

| 参数名           | 类型      | 必填 | 说明     |
|---------------|---------|----|--------|
| name          | String  | 是  | 收件人姓名  |
| phone         | String  | 是  | 收件人手机号 |
| province      | String  | 否  | 省      |
| city          | String  | 否  | 市      |
| district      | String  | 否  | 区      |
| detailAddress | String  | 是  | 详细地址   |
| isDefault     | Boolean | 否  | 是否默认地址 |

**响应数据：** `R<AddressVO>`

| 字段        | 类型   | 说明   |
|-----------|------|------|
| addressId | Long | 地址ID |

---

### 1.9 查询收货地址列表

- **接口说明**：获取当前用户所有收货地址
- **URL**：`GET /api/user/address`
- **是否需要登录**：是

**请求参数：** 无

**响应数据：** `R<List<UserAddressVO>>`

| 字段            | 类型      | 说明        |
|---------------|---------|-----------|
| addressId     | String  | 地址ID（加密后） |
| name          | String  | 收件人姓名     |
| phone         | String  | 收件人手机号    |
| province      | String  | 省         |
| city          | String  | 市         |
| district      | String  | 区         |
| detailAddress | String  | 详细地址      |
| isDefault     | Boolean | 是否默认      |

---

### 1.10 获取收货地址详情

- **接口说明**：根据加密后的地址ID获取地址详情
- **URL**：`GET /api/user/address/{addressId}`
- **是否需要登录**：是

**路径参数：**

| 参数名       | 类型     | 必填 | 说明        |
|-----------|--------|----|-----------|
| addressId | String | 是  | 地址ID（加密后） |

**响应数据：** `R<UserAddressVO>`（同上）

---

### 1.11 修改收货地址

- **接口说明**：修改指定收货地址
- **URL**：`PUT /api/user/address/{addressId}`
- **是否需要登录**：是

**路径参数：**

| 参数名       | 类型     | 必填 | 说明        |
|-----------|--------|----|-----------|
| addressId | String | 是  | 地址ID（加密后） |

**请求参数（JSON Body）：** `UserAddressDTO`（同添加地址）

**响应数据：** `R<Void>`

---

### 1.12 删除收货地址

- **接口说明**：删除指定收货地址
- **URL**：`DELETE /api/user/address/{addressId}`
- **是否需要登录**：是

**路径参数：**

| 参数名       | 类型     | 必填 | 说明        |
|-----------|--------|----|-----------|
| addressId | String | 是  | 地址ID（加密后） |

**响应数据：** `R<Void>`

---

### 1.13 内部: 获取收货地址详情

- **接口说明**：内部Feign调用，根据原始地址ID获取地址详情
- **URL**：`GET /api/user/address/inner/{addressId}`
- **是否需要登录**：否（内部调用）

**请求参数：**

| 参数名           | 类型   | 必填 | 说明         |
|---------------|------|----|------------|
| addressId（路径） | Long | 是  | 地址ID（原始ID） |
| userId（查询参数）  | Long | 是  | 用户ID       |

**响应数据：** `UserAddressVO`（不包装 R，直接返回）

---

## 2. 商品服务（item-service）

### 2.1 创建 SPU（管理端）

- **接口说明**：创建商品 SPU（图片需先通过 OSS 直传获取 URL）
- **URL**：`POST /api/admin/spu`
- **是否需要登录**：是（管理端）

**请求参数（JSON Body）：**

| 参数名         | 类型             | 必填 | 说明                          |
|-------------|----------------|----|-----------------------------|
| spuCode     | String         | 是  | SPU编码                       |
| name        | String         | 是  | 商品名称                        |
| categoryId  | Long           | 是  | 分类ID                        |
| brandId     | Long           | 是  | 品牌ID                        |
| description | String         | 否  | 商品描述                        |
| mainImage   | String         | 是  | 主图URL（通过 OSS 直传获取）          |
| images      | List\<String\> | 否  | 商品图片列表（通过 OSS 直传获取的 URL 数组） |
| status      | Integer        | 否  | 状态：0-下架 1-上架（默认1）           |

**响应数据：** `R<SpuCreateResultVO>`

---

### 2.2 查询 SPU 列表（管理端）

- **接口说明**：查询商品SPU列表（管理端，从数据库查询）
- **URL**：`GET /api/admin/spu/list`
- **是否需要登录**：是（管理端）

**请求参数（Query）：**

| 参数名        | 类型      | 必填 | 说明         |
|------------|---------|----|------------|
| pageNo     | Integer | 否  | 页码（默认1）    |
| pageSize   | Integer | 否  | 每页数量（默认20） |
| status     | Integer | 否  | 状态过滤       |
| categoryId | Long    | 否  | 分类ID       |
| keyword    | String  | 否  | 关键词搜索      |

**响应数据：** `R<PageDTO<SpuListVO>>`

| 字段    | 类型                | 说明    |
|-------|-------------------|-------|
| total | Long              | 总记录数  |
| pages | Long              | 总页数   |
| list  | List\<SpuListVO\> | SPU列表 |

---

### 2.3 创建 SKU（管理端）

- **接口说明**：创建商品 SKU
- **URL**：`POST /api/admin/sku`
- **是否需要登录**：是（管理端）

**请求参数（JSON Body）：**

| 参数名            | 类型             | 必填 | 说明                           |
|----------------|----------------|----|------------------------------|
| spuId          | Long           | 是  | 所属SPU ID                     |
| skuCode        | String         | 是  | SKU编码                        |
| price          | BigDecimal     | 是  | 价格                           |
| stock          | Integer        | 是  | 库存数量                         |
| specifications | Map            | 否  | 规格参数（如 {"颜色":"红色","尺寸":"M"}） |
| images         | List\<String\> | 否  | SKU图片                        |

**响应数据：** `R<SkuCreateResultVO>`

---

### 2.4 更新 SKU 库存（管理端）

- **接口说明**：更新SKU库存
- **URL**：`PUT /api/admin/sku/{skuId}/stock`
- **是否需要登录**：是（管理端）

**路径参数：**

| 参数名   | 类型   | 必填 | 说明     |
|-------|------|----|--------|
| skuId | Long | 是  | SKU ID |

**请求参数（JSON Body）：**

| 参数名   | 类型      | 必填 | 说明       |
|-------|---------|----|----------|
| stock | Integer | 是  | 更新后的库存数量 |

**响应数据：** `R<SkuStockUpdateResultVO>`

---

### 2.5 获取分类树

- **接口说明**：获取商品分类树形结构
- **URL**：`GET /api/category/tree`
- **是否需要登录**：否

**请求参数（Query）：**

| 参数名   | 类型      | 必填 | 说明              |
|-------|---------|----|-----------------|
| id    | Long    | 否  | 父分类ID（默认0，表示顶级） |
| level | Integer | 否  | 查询层级深度（默认3）     |

**响应数据：** `R<List<CategoryVO>>`

| 字段       | 类型                 | 说明    |
|----------|--------------------|-------|
| id       | Long               | 分类ID  |
| name     | String             | 分类名称  |
| parentId | Long               | 父分类ID |
| level    | Integer            | 层级    |
| children | List\<CategoryVO\> | 子分类列表 |

---

### 2.6 获取商品详情

- **接口说明**：根据SPU ID获取商品详情（含SKU列表）
- **URL**：`GET /api/product/detail/{spuId}`
- **是否需要登录**：否

**路径参数：**

| 参数名   | 类型   | 必填 | 说明     |
|-------|------|----|--------|
| spuId | Long | 是  | SPU ID |

**响应数据：** `R<ProductDetailVO>`

| 字段          | 类型                | 说明            |
|-------------|-------------------|---------------|
| spuId       | Long              | SPU ID        |
| name        | String            | 商品名称          |
| mainImage   | String            | 主图            |
| description | String            | 描述            |
| categoryId  | Long              | 分类ID          |
| brandId     | Long              | 品牌ID          |
| skuList     | List\<SkuInfoVO\> | SKU列表（含价格、库存） |

---

### 2.7 查询 SKU 库存

- **接口说明**：根据SKU ID查询库存
- **URL**：`GET /api/product/stock/{skuId}`
- **是否需要登录**：否

**路径参数：**

| 参数名   | 类型   | 必填 | 说明     |
|-------|------|----|--------|
| skuId | Long | 是  | SKU ID |

**响应数据：** `R<StockVO>`

| 字段    | 类型      | 说明     |
|-------|---------|--------|
| skuId | Long    | SKU ID |
| stock | Integer | 当前库存   |

---

### 2.8 内部: 获取商品详情

- **接口说明**：内部Feign调用，获取商品详情（不包装R）
- **URL**：`GET /api/product/inner/detail/{spuId}`
- **是否需要登录**：否（内部调用）

**参数/响应：** 同 2.6，但不包装在 R 中

---

### 2.9 内部: 查询 SKU 库存

- **接口说明**：内部Feign调用，查询SKU库存
- **URL**：`GET /api/product/inner/stock/{skuId}`
- **是否需要登录**：否（内部调用）

**参数/响应：** 同 2.7，但不包装在 R 中

---

### 2.10 内部: 获取商品 SKU 编码

- **接口说明**：内部Feign调用，获取SKU编码
- **URL**：`GET /api/product/inner/getCode?skuId={skuId}`
- **是否需要登录**：否（内部调用）

**请求参数（Query）：**

| 参数名   | 类型   | 必填 | 说明     |
|-------|------|----|--------|
| skuId | Long | 是  | SKU ID |

**响应数据：** `ProductStringDTO`（编码信息）

---

### 2.11 内部: 扣减库存

- **接口说明**：内部Feign调用，扣减指定SKU库存
- **URL**：`GET /api/product/inner/deductStock?skuId={skuId}&stock={stock}`
- **是否需要登录**：否（内部调用）

**请求参数（Query）：**

| 参数名   | 类型      | 必填 | 说明     |
|-------|---------|----|--------|
| skuId | Long    | 是  | SKU ID |
| stock | Integer | 是  | 扣减数量   |

**响应数据：** `Boolean`（成功/失败）

---

### 2.12 内部: 批量扣减库存

- **接口说明**：内部Feign调用，批量扣减库存
- **URL**：`POST /api/product/inner/batchDeductStock`
- **是否需要登录**：否（内部调用）

**请求参数（JSON Body）：** `List<BatchDeductStockDTO>`

| 参数名   | 类型      | 必填 | 说明     |
|-------|---------|----|--------|
| skuId | Long    | 是  | SKU ID |
| stock | Integer | 是  | 扣减数量   |

**响应数据：** `List<BatchDeductStockResultVO>`

---

### 2.13 内部: 批量获取商品信息（预热用）

- **接口说明**：内部Feign调用，批量获取商品信息（供秒杀预热使用）
- **URL**：`POST /api/product/inner/getProductInfo`
- **是否需要登录**：否（内部调用）

**请求参数（JSON Body）：** `List<SeckillProductSkuDTO>`

| 参数名       | 类型   | 必填 | 说明     |
|-----------|------|----|--------|
| productId | Long | 是  | 商品ID   |
| skuId     | Long | 是  | SKU ID |

**响应数据：** `Map<String, Map<String, String>>`

---

### 2.14 内部: 获取 SKU 详细信息

- **接口说明**：内部Feign调用，获取SKU详细信息
- **URL**：`GET /api/product/inner/getSkuDetail?skuId={skuId}`
- **是否需要登录**：否（内部调用）

**请求参数（Query）：**

| 参数名   | 类型   | 必填 | 说明     |
|-------|------|----|--------|
| skuId | Long | 是  | SKU ID |

**响应数据：** `SkuDetailVO`

| 字段             | 类型         | 说明     |
|----------------|------------|--------|
| skuId          | Long       | SKU ID |
| price          | BigDecimal | 价格     |
| stock          | Integer    | 库存     |
| image          | String     | SKU图片  |
| specifications | Map        | 规格参数   |

---

### 2.15 内部: 恢复库存

- **接口说明**：内部Feign调用，恢复SKU库存（取消订单时使用）
- **URL**：`GET /api/product/inner/addStock?skuId={skuId}&stock={stock}`
- **是否需要登录**：否（内部调用）

**请求参数（Query）：**

| 参数名   | 类型      | 必填 | 说明     |
|-------|---------|----|--------|
| skuId | Long    | 是  | SKU ID |
| stock | Integer | 是  | 恢复数量   |

**响应数据：** `Boolean`（成功/失败）

---

### 2.16 获取 OSS 上传凭证（管理端上传商品图片）

- **接口说明**：获取阿里云 OSS 前端直传凭证，用于管理端上传商品图片
- **URL**：`GET /api/admin/oss/credential`
- **是否需要登录**：是（管理端）

**请求参数：** 无

**响应数据：** `R<OssUploadCredentialVO>`

| 字段              | 类型     | 说明                  |
|-----------------|--------|---------------------|
| accessKeyId     | String | 临时 AccessKey ID     |
| accessKeySecret | String | 临时 AccessKey Secret |
| securityToken   | String | 安全令牌（STS Token）     |
| endpoint        | String | OSS 访问域名            |
| bucketName      | String | Bucket 名称           |
| filePrefix      | String | 文件存储路径前缀（item）      |
| expiration      | Long   | 凭证过期时间（秒）           |
| regionId        | String | OSS Region ID       |

**限制规则：**

- 凭证有效期为 15 分钟（默认）
- 凭证仅允许上传到 `item/` 目录
- 管理端无频率限制（可上传多张商品图片）

**前端使用流程：**

1. 调用 `GET /api/admin/oss/credential` 获取 STS 临时凭证
2. 使用 `ali-oss` SDK 创建 OSS 客户端（填入凭证信息）
3. 调用 `client.put(fileName, file)` 直接将文件上传到 OSS
4. 获取返回的 URL 后提交到 SPU/SKU 创建接口

---

## 3. 订单服务（order-service）

### 3.1 创建普通订单

- **接口说明**：创建普通商品订单
- **URL**：`POST /api/order/create`
- **是否需要登录**：是

**请求参数（JSON Body）：**

| 参数名       | 类型                   | 必填 | 说明     |
|-----------|----------------------|----|--------|
| addressId | Long                 | 是  | 收货地址ID |
| remark    | String               | 否  | 备注     |
| items     | List\<OrderItemDTO\> | 是  | 商品列表   |

**OrderItemDTO：**

| 参数名      | 类型         | 必填 | 说明     |
|----------|------------|----|--------|
| skuId    | Long       | 是  | SKU ID |
| quantity | Integer    | 是  | 购买数量   |
| price    | BigDecimal | 是  | 单价     |

**响应数据：** `R<OrderDetailVO>`

| 字段          | 类型                  | 说明     |
|-------------|---------------------|--------|
| orderNo     | String              | 订单号    |
| totalAmount | BigDecimal          | 订单总金额  |
| status      | Integer             | 订单状态   |
| orderItems  | List\<OrderItemVO\> | 订单商品列表 |
| addressInfo | AddressVO           | 收货地址信息 |
| createTime  | LocalDateTime       | 创建时间   |

---

### 3.2 获取普通订单详情

- **接口说明**：根据订单号获取普通订单详情
- **URL**：`GET /api/order/detail/{orderNo}`
- **是否需要登录**：是

**路径参数：**

| 参数名     | 类型     | 必填 | 说明  |
|---------|--------|----|-----|
| orderNo | String | 是  | 订单号 |

**响应数据：** `R<OrderDetailVO>`

---

### 3.3 取消普通订单

- **接口说明**：取消指定的普通订单
- **URL**：`POST /api/order/cancel/{orderNo}`
- **是否需要登录**：是

**路径参数：**

| 参数名     | 类型     | 必填 | 说明  |
|---------|--------|----|-----|
| orderNo | String | 是  | 订单号 |

**请求参数（JSON Body）：**

| 参数名          | 类型     | 必填 | 说明   |
|--------------|--------|----|------|
| cancelReason | String | 否  | 取消原因 |

**响应数据：** `R<CancelOrderResponseVO>`

---

### 3.4 获取普通订单列表

- **接口说明**：分页查询当前用户的普通订单列表
- **URL**：`GET /api/order/list`
- **是否需要登录**：是

**请求参数（Query）：**

| 参数名      | 类型      | 必填 | 说明         |
|----------|---------|----|------------|
| pageNum  | Integer | 否  | 页码（默认1）    |
| pageSize | Integer | 否  | 每页数量（默认10） |
| status   | Integer | 否  | 订单状态过滤     |

**响应数据：** `R<PageResult<OrderListItemVO>>`

| 字段       | 类型                      | 说明   |
|----------|-------------------------|------|
| total    | Long                    | 总记录数 |
| pageNum  | Integer                 | 当前页码 |
| pageSize | Integer                 | 每页大小 |
| list     | List\<OrderListItemVO\> | 订单列表 |

---

### 3.5 创建支付

- **接口说明**：发起支付，返回支付表单信息
- **URL**：`POST /api/payment/pay`
- **是否需要登录**：是

**请求参数（JSON Body）：**

| 参数名     | 类型      | 必填 | 说明                      |
|---------|---------|----|-------------------------|
| orderNo | String  | 是  | 订单号                     |
| payType | Integer | 是  | 支付方式：1-微信支付 2-支付宝 3-银行卡 |

**响应数据：** `R<PayResponseVO>`

| 字段          | 类型         | 说明                |
|-------------|------------|-------------------|
| payForm     | String     | 支付表单HTML（跳转第三方支付） |
| orderNo     | String     | 订单号               |
| totalAmount | BigDecimal | 支付金额              |

---

### 3.6 支付宝支付回调

- **接口说明**：支付宝异步通知回调接口（接收支付宝的支付结果通知）
- **URL**：`POST /api/payment/callback/alipay`
- **是否需要登录**：否（支付宝服务端调用）

**请求参数：** HttpServletRequest（支付宝回调参数）

| 参数名          | 类型     | 说明                    |
|--------------|--------|-----------------------|
| out_trade_no | String | 商户订单号                 |
| trade_no     | String | 支付宝交易号                |
| trade_status | String | 交易状态（TRADE_SUCCESS 等） |
| total_amount | String | 付款金额                  |

**响应数据：** `String`（"success" / "fail"）

> **注意**：由于使用 SakuraFrp 端口映射且仅支持 HTTPS 野证书，异步回调可能无法成功接收。支付成功后前端会跳转到同步回调页面。

---

### 3.7 查询支付结果

- **接口说明**：查询指定订单的支付结果
- **URL**：`GET /api/payment/result/{orderNo}`
- **是否需要登录**：是

**路径参数：**

| 参数名     | 类型     | 必填 | 说明  |
|---------|--------|----|-----|
| orderNo | String | 是  | 订单号 |

**响应数据：** `R<PayResultVO>`

| 字段          | 类型            | 说明     |
|-------------|---------------|--------|
| orderNo     | String        | 订单号    |
| payStatus   | Integer       | 支付状态   |
| payTime     | LocalDateTime | 支付时间   |
| totalAmount | BigDecimal    | 支付金额   |
| tradeNo     | String        | 第三方交易号 |

---

### 3.8 申请退款

- **接口说明**：用户发起退款申请
- **URL**：`POST /api/payment/refund`
- **是否需要登录**：是

**请求参数（JSON Body）：**

| 参数名          | 类型         | 必填 | 说明          |
|--------------|------------|----|-------------|
| orderNo      | String     | 是  | 订单号         |
| refundAmount | BigDecimal | 是  | 退款金额（≥0.01） |
| refundReason | String     | 否  | 退款原因        |

**响应数据：** `R<RefundResponseVO>`

---

### 3.9 审核退款（管理端）

- **接口说明**：管理端审核退款申请
- **URL**：`POST /api/admin/payment/refund`
- **是否需要登录**：是（管理端）

**请求参数（JSON Body）：**

| 参数名         | 类型      | 必填 | 说明             |
|-------------|---------|----|----------------|
| refundId    | Long    | 是  | 退款记录ID         |
| auditStatus | Integer | 是  | 审核状态：1-通过 2-驳回 |
| auditRemark | String  | 否  | 审核备注           |

**响应数据：** `R<Void>`

---

### 3.10 获取退款列表（管理端）

- **接口说明**：管理端分页查询退款申请列表
- **URL**：`GET /api/admin/payment/refund/list`
- **是否需要登录**：是（管理端）

**请求参数（Query）：**

| 参数名      | 类型      | 必填 | 说明         |
|----------|---------|----|------------|
| pageNum  | Integer | 否  | 页码（默认1）    |
| pageSize | Integer | 否  | 每页数量（默认10） |
| status   | Integer | 否  | 退款状态过滤     |

**响应数据：** `R<PageResult<RefundListItemVO>>`

---

### 3.11 取消秒杀订单

- **接口说明**：取消秒杀订单
- **URL**：`POST /api/order/seckill/cancel/{orderNo}`
- **是否需要登录**：是

**路径参数：**

| 参数名     | 类型     | 必填 | 说明  |
|---------|--------|----|-----|
| orderNo | String | 是  | 订单号 |

**请求参数（JSON Body）：**

| 参数名          | 类型     | 必填 | 说明   |
|--------------|--------|----|------|
| cancelReason | String | 否  | 取消原因 |

**响应数据：** `R<CancelOrderResponseVO>`

---

### 3.12 获取秒杀订单详情

- **接口说明**：获取秒杀订单详情
- **URL**：`GET /api/order/seckill/detail/{orderNo}`
- **是否需要登录**：是

**路径参数：**

| 参数名     | 类型     | 必填 | 说明  |
|---------|--------|----|-----|
| orderNo | String | 是  | 订单号 |

**响应数据：** `R<OrderDetailVO>`

---

### 3.13 获取秒杀订单列表

- **接口说明**：分页查询当前用户的秒杀订单列表
- **URL**：`GET /api/order/seckill/list`
- **是否需要登录**：是

**请求参数（Query）：**

| 参数名      | 类型      | 必填 | 说明         |
|----------|---------|----|------------|
| pageNum  | Integer | 否  | 页码（默认1）    |
| pageSize | Integer | 否  | 每页数量（默认10） |
| status   | Integer | 否  | 订单状态过滤     |

**响应数据：** `R<PageResult<OrderListItemVO>>`

---

## 4. 秒杀服务（seckill-service）

### 4.1 生成秒杀令牌

- **接口说明**：前端在执行秒杀前，先请求生成令牌（防刷）
- **URL**：`POST /api/seckill/token`
- **是否需要登录**：是

**请求参数（JSON Body）：**

| 参数名              | 类型         | 必填 | 说明         |
|------------------|------------|----|------------|
| activityId       | Long       | 是  | 活动ID       |
| seckillProductId | Long       | 是  | 秒杀商品ID     |
| clientInfo       | ClientInfo | 否  | 客户端信息（防刷用） |

**ClientInfo：**

| 参数名       | 类型     | 必填 | 说明   |
|-----------|--------|----|------|
| deviceId  | String | 否  | 设备ID |
| userAgent | String | 否  | 用户代理 |

**响应数据：** `R<SeckillTokenVO>`

| 字段         | 类型            | 说明     |
|------------|---------------|--------|
| token      | String        | 秒杀令牌   |
| expireTime | LocalDateTime | 令牌过期时间 |

---

### 4.2 执行秒杀

- **接口说明**：执行秒杀下单操作
- **URL**：`POST /api/seckill/execute`
- **是否需要登录**：是

**请求参数（JSON Body）：**

| 参数名              | 类型      | 必填 | 说明            |
|------------------|---------|----|---------------|
| requestId        | String  | 是  | 客户端唯一请求ID（幂等） |
| activityId       | Long    | 是  | 活动ID          |
| seckillProductId | Long    | 是  | 秒杀商品ID        |
| skuId            | Long    | 是  | SKU ID        |
| seckillToken     | String  | 是  | 秒杀令牌          |
| quantity         | Integer | 是  | 购买数量（≥1）      |
| addressId        | String  | 是  | 收货地址ID（加密后）   |

**响应数据：** `R<?>`

**成功时（秒杀成功）：** `code=200`

| 字段          | 类型         | 说明   |
|-------------|------------|------|
| orderNo     | String     | 订单号  |
| totalAmount | BigDecimal | 支付金额 |

**排队中：** `code=2001`

| 字段        | 类型     | 说明           |
|-----------|--------|--------------|
| requestId | String | 请求ID（用于轮询结果） |
| status    | String | PROCESSING   |

---

### 4.3 查询秒杀结果

- **接口说明**：前端轮询查询秒杀执行结果
- **URL**：`GET /api/seckill/result/{requestId}`
- **是否需要登录**：是

**路径参数：**

| 参数名       | 类型     | 必填 | 说明   |
|-----------|--------|----|------|
| requestId | String | 是  | 请求ID |

**响应数据：** `R<?>`

**状态码说明：**

| code | message | 说明          |
|------|---------|-------------|
| 200  | 秒杀成功    | 秒杀成功，返回订单信息 |
| 1003 | 库存不足    | 秒杀失败，库存不足   |
| 1004 | 活动已结束   | 秒杀失败，活动已结束  |
| 1005 | 活动未开始   | 秒杀失败，活动未开始  |
| 1007 | 活动已过期   | 秒杀失败，活动已过期  |
| 1006 | 秒杀失败    | 其他原因失败      |
| 2001 | 处理中     | 仍在处理中，继续轮询  |

---

### 4.4 查询秒杀活动列表

- **接口说明**：从 Redis 缓存查询秒杀活动列表
- **URL**：`GET /api/seckill/activities`
- **是否需要登录**：否

**请求参数（Query）：**

| 参数名    | 类型      | 必填 | 说明                     |
|--------|---------|----|------------------------|
| status | Integer | 否  | 活动状态：0-未开始 1-进行中 2-已结束 |

**响应数据：** `R<List<SeckillActivityCacheVO>>`

| 字段           | 类型            | 说明   |
|--------------|---------------|------|
| activityId   | Long          | 活动ID |
| activityName | String        | 活动名称 |
| startTime    | LocalDateTime | 开始时间 |
| endTime      | LocalDateTime | 结束时间 |
| status       | Integer       | 活动状态 |
| activityDesc | String        | 活动描述 |

---

### 4.5 查询秒杀活动详情（含商品列表）

- **接口说明**：查询单个秒杀活动详情，包含该活动下的秒杀商品列表
- **URL**：`GET /api/seckill/activity/{activityId}/products`
- **是否需要登录**：否

**路径参数：**

| 参数名        | 类型   | 必填 | 说明   |
|------------|------|----|------|
| activityId | Long | 是  | 活动ID |

**响应数据：** `R<SeckillActivityDetailVO>`

| 字段           | 类型                           | 说明     |
|--------------|------------------------------|--------|
| activityInfo | SeckillActivityCacheVO       | 活动信息   |
| productList  | List\<SeckillProductInfoVO\> | 秒杀商品列表 |

---

### 4.6 查询秒杀商品详情

- **接口说明**：查询秒杀商品详情
- **URL**：`GET /api/seckill/product/{seckillProductId}`
- **是否需要登录**：否

**路径参数：**

| 参数名              | 类型   | 必填 | 说明     |
|------------------|------|----|--------|
| seckillProductId | Long | 是  | 秒杀商品ID |

**请求参数（Query）：**

| 参数名        | 类型   | 必填 | 说明   |
|------------|------|----|------|
| activityId | Long | 是  | 活动ID |

**响应数据：** `R<SeckillProductInfoVO>`

| 字段               | 类型         | 说明     |
|------------------|------------|--------|
| seckillProductId | Long       | 秒杀商品ID |
| productId        | Long       | 商品ID   |
| skuId            | Long       | SKU ID |
| seckillPrice     | BigDecimal | 秒杀价    |
| seckillStock     | Integer    | 秒杀库存   |
| limitPerUser     | Integer    | 每人限购   |

---

### 4.7 查询用户秒杀记录

- **接口说明**：分页查询当前用户的秒杀记录
- **URL**：`GET /api/seckill/user/records`
- **是否需要登录**：是

**请求参数（Query）：**

| 参数名      | 类型      | 必填 | 说明         |
|----------|---------|----|------------|
| pageNum  | Integer | 否  | 页码（默认1）    |
| pageSize | Integer | 否  | 每页数量（默认10） |

**响应数据：** `R<PageResult<UserSeckillRecordVO>>`

| 字段       | 类型                          | 说明     |
|----------|-----------------------------|--------|
| total    | Long                        | 总记录数   |
| pageNum  | Integer                     | 当前页码   |
| pageSize | Integer                     | 每页大小   |
| list     | List\<UserSeckillRecordVO\> | 秒杀记录列表 |

---

### 4.8 创建秒杀活动（管理端）

- **接口说明**：管理端创建秒杀活动
- **URL**：`POST /api/admin/seckill/activity`
- **是否需要登录**：是（管理端）

**请求参数（JSON Body）：**

| 参数名          | 类型            | 必填 | 说明                           |
|--------------|---------------|----|------------------------------|
| activityName | String        | 是  | 活动名称                         |
| startTime    | LocalDateTime | 是  | 开始时间（格式：yyyy-MM-dd HH:mm:ss） |
| endTime      | LocalDateTime | 是  | 结束时间                         |
| activityDesc | String        | 否  | 活动描述                         |
| limitPerUser | Integer       | 是  | 每人限购数量（>0）                   |

**响应数据：** `R<SeckillActivityCreateResultVO>`

---

### 4.9 修改秒杀活动（管理端）

- **接口说明**：管理端修改秒杀活动信息
- **URL**：`PUT /api/admin/seckill/activity/{activityId}`
- **是否需要登录**：是（管理端）

**路径参数：**

| 参数名        | 类型   | 必填 | 说明   |
|------------|------|----|------|
| activityId | Long | 是  | 活动ID |

**请求参数（JSON Body）：** `SeckillActivityUpdateDTO`

| 参数名          | 类型            | 必填 | 说明     |
|--------------|---------------|----|--------|
| activityName | String        | 否  | 活动名称   |
| startTime    | LocalDateTime | 否  | 开始时间   |
| endTime      | LocalDateTime | 否  | 结束时间   |
| activityDesc | String        | 否  | 活动描述   |
| limitPerUser | Integer       | 否  | 每人限购数量 |

**响应数据：** `R<Void>`

---

### 4.10 回显秒杀活动（管理端）

- **接口说明**：管理端回显秒杀活动信息（用于编辑回填）
- **URL**：`GET /api/admin/seckill/activity/{id}`
- **是否需要登录**：是（管理端）

**路径参数：**

| 参数名 | 类型   | 必填 | 说明   |
|-----|------|----|------|
| id  | Long | 是  | 活动ID |

**响应数据：** `R<SeckillActivityUpdateDTO>`

---

### 4.11 添加秒杀商品（管理端）

- **接口说明**：管理端为秒杀活动添加单个商品
- **URL**：`POST /api/admin/seckill/activity/{activityId}/product`
- **是否需要登录**：是（管理端）

**路径参数：**

| 参数名        | 类型   | 必填 | 说明   |
|------------|------|----|------|
| activityId | Long | 是  | 活动ID |

**请求参数（JSON Body）：**

| 参数名          | 类型         | 必填 | 说明     |
|--------------|------------|----|--------|
| productId    | Long       | 是  | 商品ID   |
| skuId        | Long       | 是  | SKU ID |
| seckillPrice | BigDecimal | 是  | 秒杀价格   |
| seckillStock | Integer    | 是  | 秒杀库存   |

**响应数据：** `R<SeckillActivityAddPVO>`

---

### 4.12 批量添加秒杀商品（管理端）

- **接口说明**：管理端为秒杀活动批量添加商品
- **URL**：`POST /api/admin/seckill/activity/{activityId}/products/batch`
- **是否需要登录**：是（管理端）

**路径参数：**

| 参数名        | 类型   | 必填 | 说明   |
|------------|------|----|------|
| activityId | Long | 是  | 活动ID |

**请求参数（JSON Body）：**

| 参数名      | 类型                             | 必填 | 说明   |
|----------|--------------------------------|----|------|
| products | List\<SeckillActivityAddPDTO\> | 是  | 商品列表 |

**SeckillActivityAddPDTO：**

| 参数名          | 类型         | 必填 | 说明     |
|--------------|------------|----|--------|
| productId    | Long       | 是  | 商品ID   |
| skuId        | Long       | 是  | SKU ID |
| seckillPrice | BigDecimal | 是  | 秒杀价格   |
| seckillStock | Integer    | 是  | 秒杀库存   |

**响应数据：** `R<SeckillProductBatchAddResultVO>`

---

### 4.13 更新秒杀商品库存（管理端）

- **接口说明**：管理端更新秒杀商品的库存数量
- **URL**：`PUT /api/admin/seckill/product/{seckillProductId}/stock`
- **是否需要登录**：是（管理端）

**路径参数：**

| 参数名              | 类型   | 必填 | 说明     |
|------------------|------|----|--------|
| seckillProductId | Long | 是  | 秒杀商品ID |

**请求参数（JSON Body）：**

| 参数名   | 类型      | 必填 | 说明       |
|-------|---------|----|----------|
| stock | Integer | 是  | 更新后的秒杀库存 |

**响应数据：** `R<SeckillProductStockUpdateResultVO>`

---

### 4.14 活动预热（管理端）

- **接口说明**：对秒杀活动进行缓存预热（Redis + 本地双层缓存），幂等保护
- **URL**：`POST /api/admin/seckill/activity/{activityId}/preheat`
- **是否需要登录**：是（管理端）

**路径参数：**

| 参数名        | 类型   | 必填 | 说明   |
|------------|------|----|------|
| activityId | Long | 是  | 活动ID |

**请求参数（Query）：**

| 参数名   | 类型      | 必填 | 说明                |
|-------|---------|----|-------------------|
| force | Boolean | 否  | 是否强制重新预热（默认false） |

**响应数据：** `R<SeckillActivityPreheatVO>`

| 字段           | 类型      | 说明     |
|--------------|---------|--------|
| preheated    | Boolean | 是否预热成功 |
| activityId   | Long    | 活动ID   |
| productCount | Integer | 预热商品数量 |

---

## 5. 搜索服务（search-service）

### 5.1 搜索商品

- **接口说明**：基于 Elasticsearch 进行商品搜索
- **URL**：`GET /api/product/search`
- **是否需要登录**：否

**请求参数（Query）：**

| 参数名        | 类型      | 必填 | 说明                                                  |
|------------|---------|----|-----------------------------------------------------|
| keyword    | String  | 否  | 搜索关键词                                               |
| categoryId | Long    | 否  | 分类ID                                                |
| sort       | String  | 否  | 排序方式：price_asc-价格升序，price_desc-价格降序，sales_desc-销量降序 |
| pageNo     | Integer | 否  | 页码（默认1）                                             |
| pageSize   | Integer | 否  | 每页数量（默认20）                                          |

**响应数据：** `R<PageDTO<ProductSearchVO>>`

| 字段    | 类型                      | 说明     |
|-------|-------------------------|--------|
| total | Long                    | 总记录数   |
| pages | Long                    | 总页数    |
| list  | List\<ProductSearchVO\> | 搜索结果列表 |

**ProductSearchVO：**

| 字段         | 类型         | 说明     |
|------------|------------|--------|
| spuId      | Long       | SPU ID |
| name       | String     | 商品名称   |
| mainImage  | String     | 主图     |
| price      | BigDecimal | 价格     |
| sales      | Integer    | 销量     |
| categoryId | Long       | 分类ID   |

---

## 6. 内部 Feign 接口

以下接口通过 OpenFeign 进行服务间调用，不对外暴露。

### 6.1 ItemServiceClient

| 方法               | URL                                      | 说明        |
|------------------|------------------------------------------|-----------|
| getCode          | GET /api/product/inner/getCode           | 获取商品SKU编码 |
| deductStock      | GET /api/product/inner/deductStock       | 扣减库存      |
| batchDeductStock | POST /api/product/inner/batchDeductStock | 批量扣减库存    |
| getProductInfo   | POST /api/product/inner/getProductInfo   | 批量获取商品信息  |
| getSkuDetail     | GET /api/product/inner/getSkuDetail      | 获取SKU详细信息 |
| addStock         | GET /api/product/inner/addStock          | 恢复库存      |

### 6.2 UserServiceClient

| 方法               | URL                                     | 说明       |
|------------------|-----------------------------------------|----------|
| getAddressById   | GET /api/user/address/inner/{addressId} | 获取收货地址详情 |
| getUserAddresses | GET /api/user/address                   | 获取用户地址列表 |

---

## 附录：统一响应码

| code | 说明         |
|------|------------|
| 200  | 成功         |
| 500  | 服务器内部错误    |
| 1003 | 库存不足       |
| 1004 | 活动已结束      |
| 1005 | 活动未开始      |
| 1006 | 秒杀失败（其他原因） |
| 1007 | 活动已过期      |
| 2001 | 排队中/处理中    |

---

## 七、管理统计接口 (AdminStatisticsController) — order-service

| 方法  | 路径                                           | 说明                                     |
|-----|----------------------------------------------|----------------------------------------|
| GET | `/api/admin/statistics/overview`             | 平台总览：总用户数、总订单数、总销售额、待处理退款数、今日订单数、今日销售额 |
| GET | `/api/admin/statistics/seckill/{activityId}` | 单活动销售统计                                |
| GET | `/api/admin/statistics/seckill/list`         | 活动销售统计列表（分页）                           |
| GET | `/api/admin/statistics/trends`               | 近N天销售趋势（参数: days, 默认7）                 |

### 7.1 平台总览响应

```json
{
  "totalUsers": 100,
  "totalOrders": 500,
  "totalRevenue": 123456.78,
  "pendingRefunds": 3,
  "todayOrders": 12,
  "todayRevenue": 3456.78
}
```

### 7.2 活动统计响应

```json
{
  "activityId": 1,
  "activityName": "618秒杀",
  "status": 1,
  "statusName": "进行中",
  "startTime": "2026-06-18 00:00:00",
  "endTime": "2026-06-18 23:59:59",
  "orderCount": 200,
  "totalAmount": 50000.00,
  "soldStock": 150,
  "totalStock": 500,
  "sellOutRate": 30.00,
  "avgPrice": 250.00
}
```

### 7.3 趋势响应

```json
[
  {
    "date": "2026-05-28",
    "orderCount": 15,
    "amount": 3456.78
  },
  {
    "date": "2026-05-29",
    "orderCount": 22,
    "amount": 5678.90
  }
]
```

---

## 八、管理端订单接口 (AdminOrderController) — order-service

| 方法  | 路径                                    | 说明                                                       |
|-----|---------------------------------------|----------------------------------------------------------|
| GET | `/api/admin/orders`                   | 普通订单列表（分页+筛选：orderNo/status/orderType/startDate/endDate） |
| GET | `/api/admin/orders/{orderNo}`         | 普通订单详情（含商品明细）                                            |
| PUT | `/api/admin/orders/{orderNo}/status`  | 修改订单状态（body: {status, remark}）                           |
| GET | `/api/admin/orders/seckill`           | 秒杀订单列表（分页+筛选）                                            |
| GET | `/api/admin/orders/seckill/{orderNo}` | 秒杀订单详情                                                   |

---

## 九、管理端用户接口 (AdminUserController) — user-service

| 方法  | 路径                                 | 说明                                            |
|-----|------------------------------------|-----------------------------------------------|
| GET | `/api/admin/users/count`           | 获取用户总数（内部Feign调用）                             |
| GET | `/api/admin/users`                 | 用户列表（分页+筛选：username/phone/status/memberLevel） |
| GET | `/api/admin/users/{userId}`        | 用户详情                                          |
| PUT | `/api/admin/users/{userId}/status` | 启用/禁用用户（body: {status: 0                      |1}） |
| GET | `/api/admin/users/login-logs`      | 登录日志列表（分页+筛选：userId）                          |

### 9.1 用户列表项响应

```json
{
  "userId": 1,
  "username": "admin",
  "phone": "13800138000",
  "email": "admin@example.com",
  "status": 1,
  "statusName": "正常",
  "memberLevel": 1,
  "memberLevelName": "白银",
  "createTime": "2026-01-01 00:00:00"
}
```

---

## 十、自动预热调度 (SeckillPreheatScheduler) — seckill-service

- `@Scheduled(fixedRate = 600000)` 每 10 分钟自动执行
- 查询未来 12 小时内即将开始且状态为「未开始」的活动
- 对符合条件的活动自动调用 `preheatActivity(activityId, false)`
- 已在预热中的活动（Redis 有预热标记）自动跳过
- 需在 seckill-service 启动类添加 `@EnableScheduling`

---

## 十一、管理端已有接口（回顾）

| 方法   | 路径                                                | 所属服务            | 说明              |
|------|---------------------------------------------------|-----------------|-----------------|
| POST | `/api/admin/spu`                                  | item-service    | 创建SPU           |
| GET  | `/api/admin/spu/list`                             | item-service    | SPU列表           |
| POST | `/api/admin/sku`                                  | item-service    | 创建SKU           |
| PUT  | `/api/admin/sku/{skuId}/stock`                    | item-service    | 更新SKU库存         |
| GET  | `/api/admin/oss/credential`                       | item-service    | 获取OSS凭证（商品图片上传） |
| POST | `/api/admin/seckill/activity`                     | seckill-service | 创建秒杀活动          |
| PUT  | `/api/admin/seckill/activity/{id}`                | seckill-service | 修改秒杀活动          |
| GET  | `/api/admin/seckill/activity/{id}`                | seckill-service | 回显秒杀活动          |
| POST | `/api/admin/seckill/activity/{id}/product`        | seckill-service | 添加秒杀商品          |
| POST | `/api/admin/seckill/activity/{id}/products/batch` | seckill-service | 批量添加秒杀商品        |
| PUT  | `/api/admin/seckill/product/{id}/stock`           | seckill-service | 更新秒杀商品库存        |
| POST | `/api/admin/seckill/activity/{id}/preheat`        | seckill-service | 活动预热            |
| POST | `/api/admin/payment/refund`                       | order-service   | 审核退款            |
| GET  | `/api/admin/payment/refund/list`                  | order-service   | 退款列表            |
| GET  | `/api/seckill/activities/all`                     | seckill-service | 获取所有活动（内部Feign） |
