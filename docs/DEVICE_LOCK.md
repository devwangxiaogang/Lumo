# 真机共享协议（多会话协作）

本仓库可能同时有多个 AI 会话在**同一台真机**上做验证。adb 是独占资源：
两台会话同时 install / input / logcat -c 会互相打断（对方日志被清、点击落在错误页面、
APK 被覆盖）。下面这套约定用于串行化真机访问。

## 规则

1. **拿锁才能碰设备**：任何 install / am start / input / screencap / logcat -c / run-as 之前，
   先执行 `docs/device-lock.ps1 acquire <你的会话名>`。拿不到锁就等待，不要抢。
2. **验证完立刻释放**：`docs/device-lock.ps1 release <你的会话名>`。不要长时间占着不放。
3. **锁有条目就等**：锁文件里带 owner 与时间。超过 15 分钟未释放视为失效，可强制 `-Force` 接管，
   并在回报里说明接管原因。
4. **只清自己的日志**：用 `adb logcat -d -s <Tag>:D` 精确抓取，避免 `logcat -c` 把别人的证据清掉。
   确实需要清时，先确认锁在自己手上。
5. **不改别人的运行态**：不要 force-stop 别人的被测进程来「腾地方」；需要干净状态就先拿锁。
6. **安装前先编译**：`gradlew.bat :app:assembleDebug`，避免装到旧包得出错误结论。
   两个会话并行编译会互相踩 KSP 增量（表现为 NoSuchFileException: MainActivity_GeneratedInjector.java），
   遇到时 `gradlew --stop` 后删 `app/build/generated` 重编。

## 用法

```powershell
# 拿锁（默认等 0 秒，拿不到直接报错）
powershell -ExecutionPolicy Bypass -File docs\device-lock.ps1 acquire lead

# 等最多 300 秒
powershell -ExecutionPolicy Bypass -File docs\device-lock.ps1 acquire lead -WaitSeconds 300

# 看当前持有者
powershell -ExecutionPolicy Bypass -File docs\device-lock.ps1 status

# 释放
powershell -ExecutionPolicy Bypass -File docs\device-lock.ps1 release lead
```

## 本轮分工（1.3）

| 会话 | 负责 | 设备验证重点 |
|---|---|---|
| lead（本会话） | 擂台 / 分支 / 自动标题 / 用量统计 / 面对面 | 擂台双栏、分支切换、统计页、面对面页、冷启动无 FATAL |
| 优化对话文字输入框设计 | 聊天输入区布局 | 输入框展开/长文本/键盘、发送链路不崩 |

设备只有一台（`55QCS4Z5VGGU8HLV`），**按锁排队**。
