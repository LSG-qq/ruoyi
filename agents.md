# 项目说明

- ruoyi-ui：管理后台的前端工程
- ruoyi-admin：后端服务
- ruoyi-admin：管理后台的接口模块

# server代码规范

- 代码调用流程为 controller => biz => service => mapper，其中业务写在biz层，service只需要实现简单的增删查改
- 一个类代码不超过500行
- 一个方法代码不超过50行
- 基础的service类放在ruoyi-system模块中
- 基础的service类中，增删查改方法的命名请参考：create、deleteByXxx、findByXxx、findPage、updateByXxx，方法命名无需重复携带模块名称。
- 实体类不要增加表以外的字段，如果controller返回需要返回除了实体类以外的字段，需要单独构建一个VO类
- 代码注释率需大于20%
- 代码注释统一使用中文

# admin-front代码规范

- 请单独在 `src/api` 中定义接口文件，需要按照模块进行划分。
- 页面放在 `views` 目录下
- 页面中单独封装的组件需要放在 `components` 目录下，并通过模块进行划分
- 每个`vue`文件代码尽量不超过500行
- 新建&编辑窗口需要单独封装为组件，组件内提供open方法用于打开窗口，组件需通过ref进行调用
- 代码注释率需大于20%
- 代码注释统一使用中文

# 特别说明

- 你需要根据我提示词中的给定的模块来划分前后端模块，无需每次都创建一个新的模块
