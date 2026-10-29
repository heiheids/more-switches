---
name: 问题反馈 / Bug report
about: 报告一个开关不生效或界面显示异常
title: "[Bug] "
labels: bug
---

## 问题描述

哪个开关？出现了什么现象？（例如：打开后按 Q 仍然丢出物品）

## 复现步骤

1.
2.
3.

## 环境

- Minecraft 版本：
- Fabric Loader 版本：
- Fabric API 版本：
- 模组版本：1.0.0
- 是否单人游戏：
- 界面语言（自动/中文/English）：

## 日志

`logs/latest.log` 里搜索 `moreswitches`，把相关行贴在这里。
每个开关在启动和拨动时都会写一行日志，能看出它是否被识别为开启。

```
（粘贴日志）
```

## 补充

如果是"某个开关没生效"，请先确认：

- 该开关在界面上显示为绿色（开启）
- `config/moreswitches.json` 里对应的值确实是 `true`
- 触发条件成立（例如下雨睡觉需要真的在下雨；烟花冷却需要手持烟花火箭）
