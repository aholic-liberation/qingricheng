# 轻日程人设卡格式

人设卡是一张 JSON 文件。App 内置一张示例卡；你可以在「设置 → 人设卡」导出
`example.persona.json`，按同样结构修改后导入。

## 多选规则

- 可以同时勾选多张卡。
- 勾选顺序中第一张是「主人设」，决定头像、名字、称呼和主要口吻。
- 其余卡作为补充特质/知识；设定冲突时以第一张为准。
- 不想用任何人设时，全部取消勾选，App 会退回通用陪伴助手。

## 字段

| 字段 | 必填 | 说明 |
|---|---|---|
| schemaVersion | 否 | 固定 1，便于以后升级 |
| id | 建议 | 唯一标识；不填会自动生成 |
| name | 是 | 角色名 |
| avatar | 否 | emoji，默认 🌙 |
| tagline | 否 | 一句话定位 |
| description | 否 | 角色简介 |
| systemPrompt | 建议 | 最重要的系统提示词；不填会用其他字段拼一个 |
| greeting | 否 | 首次对话开场白 |
| traits | 否 | 性格关键词数组 |
| speakingStyle | 否 | 说话方式 |
| boundaries | 否 | 边界数组 |
| exampleDialogues | 否 | `{user, assistant}` 数组 |
| memorySeeds | 否 | 希望角色初始记住的信息 |
| tags | 否 | 标签数组 |
| author / version | 否 | 作者与版本 |

也可以把多张卡放进一个数组 `[ {...}, {...} ]`，或放进 `{ "personas": [...] }` 一次导入。
