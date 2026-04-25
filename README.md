# Interval Alarm

一个只面向 Android 的闹钟 App，支持多个闹钟，每个闹钟都可以独立配置。

## 核心规则

- 每个闹钟都有一个左边界时间和右边界时间。
- 如果两个时间相同，就在这个固定时刻触发。
- 如果两个时间不同，就在这个区间内随机选择一个未来时间点触发。
- 左边界不能晚于右边界，右边界不能早于左边界，界面会自动校正。
- 可以设置生效的星期规则，支持单独某一天、工作日、周末和自定义多选。
- 新建闹钟默认每天生效。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- AlarmManager + BroadcastReceiver + Foreground Service
- DataStore

## 主要文件

- [MainActivity.kt](/D:/codexproject/alarm/app/src/main/java/com/example/intervalalarm/MainActivity.kt)
- [AlarmScheduler.kt](/D:/codexproject/alarm/app/src/main/java/com/example/intervalalarm/AlarmScheduler.kt)
- [AlarmPreferences.kt](/D:/codexproject/alarm/app/src/main/java/com/example/intervalalarm/AlarmPreferences.kt)
- [IntervalAlarmApp.kt](/D:/codexproject/alarm/app/src/main/java/com/example/intervalalarm/ui/IntervalAlarmApp.kt)

## 说明

- 当前仓库里没有 Gradle Wrapper，也没有本机 `gradle` 命令，所以这里没法直接跑构建校验。
- 工程可以直接用 Android Studio 打开，同步后即可继续运行或补全 Wrapper。
