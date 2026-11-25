// 路由配置
export interface RouteRecordRaw {
  path: string
  name?: string | symbol
  component?: any
  components?: any
  redirect?: string | Location | ((to: any) => string)
  meta?: RouteMeta
  children?: RouteRecordRaw[]
}

export interface RouteMeta {
  title?: string
  icon?: string
  isLink?: string
  isHide?: boolean
  isKeepAlive?: boolean
  isAffix?: boolean
  isIframe?: boolean
  roles?: string[]
  permissions?: string[]
  activeMenu?: string
  noTagsView?: boolean
  noCache?: boolean
  breadcrumb?: boolean
  fixed?: boolean
  level?: number
}

// 用户信息
export interface UserInfo {
  userId?: number | string
  userName?: string
  nickName?: string
  email?: string
  phonenumber?: string
  sex?: '0' | '1' | '2'
  avatar?: string
  deptId?: number | string
  deptName?: string
  roles?: Role[]
  permissions?: string[]
}

export interface Role {
  roleId?: number | string
  roleName?: string
  roleKey?: string
  roleSort?: string
  dataScope?: string
  menuCheckStrictly?: boolean
  deptCheckStrictly?: boolean
  status?: string
  roleType?: string
}

// 登录表单
export interface LoginForm {
  username: string
  password: string
  code?: string
  uuid?: string
}

// 登录结果
export interface LoginResult {
  token: string
  refreshToken?: string
  expires?: number
}

// API 响应结构
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data?: T
}

// 分页参数
export interface PageQuery {
  pageNum?: number
  pageSize?: number
  [key: string]: any
}

// 分页结果
export interface PageResult<T = any> {
  rows: T[]
  total: number
  pageNum: number
  pageSize: number
}

// 菜单
export interface Menu {
  menuId?: number
  menuName?: string
  parentId?: number
  parentName?: string
  orderNum?: number
  path?: string
  component?: string
  query?: string
  isFrame?: number
  isCache?: number
  menuType?: 'M' | 'C' | 'F'
  visible?: '0' | '1'
  status?: '0' | '1'
  perms?: string
  icon?: string
  children?: Menu[]
}

// 部门
export interface Dept {
  deptId?: number
  parentId?: number
  parentName?: string
  deptName?: string
  orderNum?: number
  leader?: string
  phone?: string
  email?: string
  status?: string
  children?: Dept[]
}

// 字典类型
export interface DictType {
  dictId?: number
  dictName?: string
  dictType?: string
  status?: string
  createTime?: string
  remark?: string
}

// 字典数据
export interface DictData {
  dictCode?: number
  dictType?: string
  dictLabel?: string
  dictValue?: string
  dictSort?: number
  cssClass?: string
  listClass?: string
  isDefault?: string
  status?: string
  remark?: string
}

// 操作日志
export interface OperLog {
  operId?: number
  title?: string
  businessType?: string
  method?: string
  requestMethod?: string
  operatorType?: string
  operName?: string
  deptName?: string
  operUrl?: string
  operIp?: string
  operLocation?: string
  operParam?: string
  jsonResult?: string
  status?: number
  errorMsg?: string
  operTime?: string
}

// 登录日志
export interface LoginLog {
  infoId?: number
  userName?: string
  ipaddr?: string
  loginLocation?: string
  browser?: string
  os?: string
  status?: string
  msg?: string
  loginTime?: string
}

// 配置信息
export interface Config {
  configId?: number
  configName?: string
  configKey?: string
  configValue?: string
  configType?: string
  isDefault?: string
  isBuiltIn?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

// ======================== AID 漫剧工坊（M1） ========================
// 后端分页返回 MP 的 Page 结构（records/total），与 system 模块的 PageResult 不同

export interface AidPageResult<T = any> {
  records: T[]
  total: number
  current: number
  size: number
}

// 创作项目
export interface AidProject {
  id?: number
  name?: string
  description?: string
  coverUrl?: string
  visualSettingId?: number
  visualSettingName?: string
  status?: string
  targetDuration?: number
  remark?: string
  episodeCount?: number
  assetCount?: number
  createTime?: string
  updateTime?: string
}

export interface AidProjectQuery {
  name?: string
  status?: string
}

// 分集
export interface AidEpisode {
  id?: number
  projectId?: number
  episodeNo?: number
  title?: string
  script?: string
  duration?: number
  status?: string
  shotCount?: number
  createTime?: string
  updateTime?: string
}

// 资产库
export interface AidAsset {
  id?: number
  projectId?: number
  assetCode?: string
  assetType?: string
  name?: string
  description?: string
  referenceUrl?: string
  promptTemplate?: string
  version?: number
  createTime?: string
  updateTime?: string
}

export interface AidAssetQuery {
  projectId?: number
  assetType?: string
  keyword?: string
}

// 视觉设定
export interface AidVisualSetting {
  id?: number
  name?: string
  scope?: string
  styleDescription?: string
  colorPalette?: string[]
  subStyles?: string[]
  constraintsList?: string[]
  negativeWords?: string[]
  aspectRatio?: string
  tailFrameSubject?: string
  tailFrameBackground?: string
  tailFrameLight?: string
  tailFrameComposition?: string
  tailFrameMood?: string
  tailFrameColors?: string[]
  snapshotTime?: number
  version?: number
  createTime?: string
  updateTime?: string
}

/** 视觉设定导出 JSON 模板；导入时 content 直接回传该对象。 */
export interface AidVisualTemplate {
  schemaVersion?: number
  name?: string
  styleDescription?: string
  colorPalette?: string[]
  subStyles?: string[]
  constraintsList?: string[]
  negativeWords?: string[]
  aspectRatio?: string
  tailFrameSubject?: string
  tailFrameBackground?: string
  tailFrameLight?: string
  tailFrameComposition?: string
  tailFrameMood?: string
  tailFrameColors?: string[]
  [key: string]: unknown
}

// 分镜
export interface AidShot {
  id?: number
  episodeId?: number
  shotNo?: number
  description?: string
  dialogue?: string
  cameraMovement?: string
  duration?: number
  imageUrl?: string
  videoUrl?: string
  promptFinal?: string
  status?: string
  createTime?: string
  updateTime?: string
}

// 生成任务
export interface AidGenerationTask {
  id?: number
  taskType?: string
  targetType?: string
  targetId?: number
  provider?: string
  model?: string
  prompt?: string
  idempotencyKey?: string
  status?: string
  progress?: number
  resultUrl?: string
  errorMessage?: string
  costAmount?: number
  retryCount?: number
  queueEntryId?: string
  visualSnapshotVersion?: number
  startedAt?: string
  completedAt?: string
  createTime?: string
}

// 通知公告
export interface Notice {
  noticeId?: number
  noticeTitle?: string
  noticeType?: string
  noticeContent?: string
  status?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  remark?: string
}
