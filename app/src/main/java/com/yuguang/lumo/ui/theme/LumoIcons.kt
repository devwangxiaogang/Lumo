package com.yuguang.lumo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 墨线图标：24dp 视口、只描边不填充，线宽 1.8dp、圆角端点。
 *
 * 为什么自绘：Material Icons 是实心几何图形，混在宣纸/朱砂的界面里像贴纸；
 * 统一到「毛笔单线」后，图标和有衬线的标题是同一套笔法。
 *
 * 用法与 Material 图标一致：`Icon(LumoIcons.Chat, contentDescription = ...)`，
 * 描边色写的是纯黑，`Icon` 的 tint 会用 SrcIn 覆写颜色，所以跟着 LocalContentColor 走。
 */
object LumoIcons {

    /** 对话：一枚带角的纸笺，内有两笔字痕。 */
    val Chat: ImageVector = inkIcon("Lumo.Chat") {
        stroke(1.8f) {
            moveTo(7.0f, 16.8f)
            lineTo(6.5f, 20.1f)
            lineTo(10.3f, 16.8f)
            lineTo(17.0f, 16.8f)
            quadTo(19.5f, 16.8f, 19.5f, 14.3f)
            lineTo(19.5f, 7.3f)
            quadTo(19.5f, 4.8f, 17.0f, 4.8f)
            lineTo(7.0f, 4.8f)
            quadTo(4.5f, 4.8f, 4.5f, 7.3f)
            lineTo(4.5f, 14.3f)
            quadTo(4.5f, 16.8f, 7.0f, 16.8f)
            close()
        }
        stroke(1.6f) {
            moveTo(8.2f, 9.4f)
            lineTo(15.8f, 9.4f)
            moveTo(8.2f, 12.4f)
            lineTo(12.8f, 12.4f)
        }
    }

    /** 翻译：两张错叠的纸笺，一张是原文、一张是译文。 */
    val Translate: ImageVector = inkIcon("Lumo.Translate") {
        stroke(1.8f) {
            // 后一张：只画可见的两条边，避免交叠处糊成一团。
            moveTo(12.4f, 7.3f)
            lineTo(12.4f, 6.6f)
            quadTo(12.4f, 4.6f, 10.4f, 4.6f)
            lineTo(5.6f, 4.6f)
            quadTo(3.6f, 4.6f, 3.6f, 6.6f)
            lineTo(3.6f, 11.4f)
            quadTo(3.6f, 13.4f, 5.6f, 13.4f)
            lineTo(6.6f, 13.4f)
        }
        stroke(1.8f) {
            // 前一张：完整纸笺。
            moveTo(10.0f, 8.4f)
            lineTo(18.4f, 8.4f)
            quadTo(20.4f, 8.4f, 20.4f, 10.4f)
            lineTo(20.4f, 17.0f)
            quadTo(20.4f, 19.0f, 18.4f, 19.0f)
            lineTo(10.0f, 19.0f)
            quadTo(8.0f, 19.0f, 8.0f, 17.0f)
            lineTo(8.0f, 10.4f)
            quadTo(8.0f, 8.4f, 10.0f, 8.4f)
            close()
        }
    }

    /** 语音：左侧一声源，向右荡开两道声波。 */
    val Voice: ImageVector = inkIcon("Lumo.Voice") {
        stroke(1.8f) {
            moveTo(4.6f, 12.0f)
            quadTo(4.6f, 10.7f, 5.9f, 10.7f)
            quadTo(7.2f, 10.7f, 7.2f, 12.0f)
            quadTo(7.2f, 13.3f, 5.9f, 13.3f)
            quadTo(4.6f, 13.3f, 4.6f, 12.0f)
            close()
        }
        stroke(1.8f) {
            moveTo(9.6f, 8.6f)
            quadTo(12.6f, 12.0f, 9.6f, 15.4f)
        }
        stroke(1.8f) {
            moveTo(13.4f, 5.9f)
            quadTo(18.2f, 12.0f, 13.4f, 18.1f)
        }
    }

    /** 设置：方印外框 + 回纹内胆。 */
    val Settings: ImageVector = inkIcon("Lumo.Settings") {
        stroke(1.8f) {
            moveTo(6.4f, 4.4f)
            lineTo(17.6f, 4.4f)
            quadTo(19.6f, 4.4f, 19.6f, 6.4f)
            lineTo(19.6f, 17.6f)
            quadTo(19.6f, 19.6f, 17.6f, 19.6f)
            lineTo(6.4f, 19.6f)
            quadTo(4.4f, 19.6f, 4.4f, 17.6f)
            lineTo(4.4f, 6.4f)
            quadTo(4.4f, 4.4f, 6.4f, 4.4f)
            close()
        }
        stroke(1.6f) {
            // 回纹：一笔折线向内盘一圈。
            moveTo(8.4f, 15.6f)
            lineTo(8.4f, 8.4f)
            lineTo(15.6f, 8.4f)
            lineTo(15.6f, 15.6f)
            lineTo(11.4f, 15.6f)
            lineTo(11.4f, 11.4f)
            lineTo(12.9f, 11.4f)
        }
    }

    /** 麦克风：胶囊话筒 + 托架 + 底座。 */
    val Mic: ImageVector = inkIcon("Lumo.Mic") {
        stroke(1.8f) {
            moveTo(9.4f, 10.0f)
            lineTo(9.4f, 7.6f)
            quadTo(9.4f, 5.0f, 12.0f, 5.0f)
            quadTo(14.6f, 5.0f, 14.6f, 7.6f)
            lineTo(14.6f, 10.0f)
            quadTo(14.6f, 12.6f, 12.0f, 12.6f)
            quadTo(9.4f, 12.6f, 9.4f, 10.0f)
            close()
        }
        stroke(1.8f) {
            moveTo(6.7f, 10.9f)
            quadTo(7.5f, 16.4f, 12.0f, 16.4f)
            quadTo(16.5f, 16.4f, 17.3f, 10.9f)
        }
        stroke(1.8f) {
            moveTo(12.0f, 16.4f)
            lineTo(12.0f, 19.2f)
            moveTo(9.3f, 19.2f)
            lineTo(14.7f, 19.2f)
        }
    }

    /** 发送：折纸笺，右侧一笔折痕。 */
    val Send: ImageVector = inkIcon("Lumo.Send") {
        stroke(1.8f) {
            moveTo(4.7f, 11.9f)
            lineTo(19.3f, 4.7f)
            lineTo(14.8f, 19.3f)
            lineTo(11.5f, 13.0f)
            close()
        }
        stroke(1.6f) {
            moveTo(11.5f, 13.0f)
            lineTo(19.3f, 4.7f)
        }
    }

    /** 返回：一道向左张开的墨弧（不加箭头，靠弯曲方向表示方向）。 */
    val Back: ImageVector = inkIcon("Lumo.Back") {
        stroke(1.9f) {
            moveTo(14.4f, 4.9f)
            quadTo(7.0f, 7.0f, 7.0f, 12.0f)
            quadTo(7.0f, 17.0f, 14.4f, 19.1f)
        }
    }

    /** 实时语音：话筒 + 向右荡开两道声波（与 [Voice] 分工：[Voice] 是放音，这枚是「开口说话」）。 */
    val Realtime: ImageVector = inkIcon("Lumo.Realtime") {
        stroke(1.8f) {
            // 话筒胶囊
            moveTo(6.5f, 8.3f)
            quadTo(6.5f, 4.3f, 8.6f, 4.3f)
            quadTo(10.7f, 4.3f, 10.7f, 8.3f)
            lineTo(10.7f, 10.6f)
            quadTo(10.7f, 14.6f, 8.6f, 14.6f)
            quadTo(6.5f, 14.6f, 6.5f, 10.6f)
            close()
        }
        stroke(1.8f) {
            // 托架 + 立柱 + 底座
            moveTo(5.0f, 11.2f)
            quadTo(5.0f, 16.4f, 8.6f, 16.4f)
            quadTo(12.2f, 16.4f, 12.2f, 11.2f)
            moveTo(8.6f, 16.4f)
            lineTo(8.6f, 19.4f)
            moveTo(6.2f, 19.4f)
            lineTo(11.0f, 19.4f)
        }
        stroke(1.6f) {
            // 两道声波
            moveTo(14.3f, 8.6f)
            quadTo(16.4f, 12.0f, 14.3f, 15.4f)
            moveTo(16.6f, 6.2f)
            quadTo(20.0f, 12.0f, 16.6f, 17.8f)
        }
    }

    /** 文字模式：三横一竖，取「文」的骨架。 */
    val TextLines: ImageVector = inkIcon("Lumo.TextLines") {
        stroke(1.8f) {
            moveTo(7.4f, 5.0f)
            lineTo(16.6f, 5.0f)
            moveTo(4.8f, 12.0f)
            lineTo(19.2f, 12.0f)
            moveTo(6.4f, 19.0f)
            lineTo(17.6f, 19.0f)
        }
        // 竖笔略带弧度，像落笔时的驻锋。
        stroke(1.8f) {
            moveTo(12.0f, 4.4f)
            quadTo(12.5f, 12.0f, 12.0f, 19.6f)
        }
    }

    /** 图片模式：圆角方框里一叠远山与一轮月。 */
    val Picture: ImageVector = inkIcon("Lumo.Picture") {
        stroke(1.8f) {
            // 画框
            moveTo(7.0f, 4.9f)
            lineTo(17.0f, 4.9f)
            quadTo(19.4f, 4.9f, 19.4f, 7.3f)
            lineTo(19.4f, 16.7f)
            quadTo(19.4f, 19.1f, 17.0f, 19.1f)
            lineTo(7.0f, 19.1f)
            quadTo(4.6f, 19.1f, 4.6f, 16.7f)
            lineTo(4.6f, 7.3f)
            quadTo(4.6f, 4.9f, 7.0f, 4.9f)
            close()
        }
        stroke(1.8f) {
            // 远山两叠
            moveTo(6.4f, 16.4f)
            quadTo(9.0f, 11.4f, 11.4f, 16.4f)
            quadTo(13.1f, 13.3f, 14.9f, 16.4f)
        }
        stroke(1.6f) {
            // 月：两段半圆弧
            moveTo(16.6f, 8.0f)
            arcTo(1.5f, 1.5f, 0f, true, true, 16.6f, 11.0f)
            arcTo(1.5f, 1.5f, 0f, true, true, 16.6f, 8.0f)
        }
    }

    /** 人脸：正面轮廓，两枚相对的小印当眼睛。 */
    val Face: ImageVector = inkIcon("Lumo.Face") {
        stroke(1.8f) {
            // 脸廓：两段半椭圆
            moveTo(5.2f, 12.2f)
            arcTo(6.8f, 7.6f, 0f, true, true, 18.8f, 12.2f)
            arcTo(6.8f, 7.6f, 0f, true, true, 5.2f, 12.2f)
        }
        stroke(1.6f) {
            // 两枚小印（左右相对，印面朝内）
            moveTo(9.0f, 10.6f)
            lineTo(11.0f, 10.6f)
            lineTo(11.0f, 12.6f)
            lineTo(9.0f, 12.6f)
            close()
            moveTo(13.0f, 10.6f)
            lineTo(15.0f, 10.6f)
            lineTo(15.0f, 12.6f)
            lineTo(13.0f, 12.6f)
            close()
        }
        stroke(1.8f) {
            // 口
            moveTo(9.8f, 15.9f)
            quadTo(12.0f, 17.6f, 14.2f, 15.9f)
        }
    }

    /** 停止：一枚落定的墨点方块。 */
    val Stop: ImageVector = inkIcon("Lumo.Stop") {
        solid {
            moveTo(7.6f, 7.6f)
            lineTo(16.4f, 7.6f)
            lineTo(16.4f, 16.4f)
            lineTo(7.6f, 16.4f)
            close()
        }
    }

    /** 播放：实心小三角。 */
    val Play: ImageVector = inkIcon("Lumo.Play") {
        solid {
            moveTo(8.6f, 5.8f)
            lineTo(18.2f, 12.0f)
            lineTo(8.6f, 18.2f)
            close()
        }
    }

    /** 复制：两张错叠的纸角。 */
    val Copy: ImageVector = inkIcon("Lumo.Copy") {
        stroke(1.7f) {
            moveTo(9.2f, 8.4f)
            lineTo(8.4f, 8.4f)
            quadTo(6.4f, 8.4f, 6.4f, 10.4f)
            lineTo(6.4f, 17.6f)
            quadTo(6.4f, 19.6f, 8.4f, 19.6f)
            lineTo(14.6f, 19.6f)
            quadTo(16.6f, 19.6f, 16.6f, 17.6f)
            lineTo(16.6f, 16.8f)
        }
        stroke(1.7f) {
            moveTo(9.8f, 4.4f)
            lineTo(16.0f, 4.4f)
            quadTo(18.0f, 4.4f, 18.0f, 6.4f)
            lineTo(18.0f, 13.6f)
            quadTo(18.0f, 15.6f, 16.0f, 15.6f)
            lineTo(9.8f, 15.6f)
            quadTo(7.8f, 15.6f, 7.8f, 13.6f)
            lineTo(7.8f, 6.4f)
            quadTo(7.8f, 4.4f, 9.8f, 4.4f)
            close()
        }
    }

    /** 朗读：一只小喇叭，向右荡开两道声波。 */
    val Speaker: ImageVector = inkIcon("Lumo.Speaker") {
        stroke(1.7f) {
            moveTo(4.4f, 9.8f)
            lineTo(7.0f, 9.8f)
            lineTo(11.0f, 6.2f)
            lineTo(11.0f, 17.8f)
            lineTo(7.0f, 14.2f)
            lineTo(4.4f, 14.2f)
            close()
        }
        stroke(1.7f) {
            moveTo(13.8f, 9.4f)
            quadTo(15.4f, 12.0f, 13.8f, 14.6f)
        }
        stroke(1.7f) {
            moveTo(16.4f, 7.2f)
            quadTo(19.4f, 12.0f, 16.4f, 16.8f)
        }
    }

    /** 关闭：两笔交叉。 */
    val Close: ImageVector = inkIcon("Lumo.Close") {
        stroke(1.8f) {
            moveTo(6.6f, 6.6f)
            lineTo(17.4f, 17.4f)
            moveTo(17.4f, 6.6f)
            lineTo(6.6f, 17.4f)
        }
    }

    /** 下折角：展开 / 收起。 */
    val ChevronDown: ImageVector = inkIcon("Lumo.ChevronDown") {
        stroke(1.9f) {
            moveTo(6.4f, 9.6f)
            lineTo(12.0f, 15.2f)
            lineTo(17.6f, 9.6f)
        }
    }

    /** 右折角：进入下一层。 */
    val ChevronRight: ImageVector = inkIcon("Lumo.ChevronRight") {
        stroke(1.9f) {
            moveTo(9.4f, 6.4f)
            lineTo(15.0f, 12.0f)
            lineTo(9.4f, 17.6f)
        }
    }

    /** 左折角：返回。 */
    val ChevronLeft: ImageVector = inkIcon("Lumo.ChevronLeft") {
        stroke(1.9f) {
            moveTo(14.6f, 6.4f)
            lineTo(9.0f, 12.0f)
            lineTo(14.6f, 17.6f)
        }
    }

    /** 交换：一上一下两个箭头。 */
    val Swap: ImageVector = inkIcon("Lumo.Swap") {
        stroke(1.7f) {
            moveTo(4.6f, 9.4f)
            lineTo(19.0f, 9.4f)
            moveTo(15.6f, 6.0f)
            lineTo(19.0f, 9.4f)
            moveTo(15.6f, 12.8f)
            lineTo(19.0f, 9.4f)
        }
        stroke(1.7f) {
            moveTo(19.4f, 14.6f)
            lineTo(5.0f, 14.6f)
            moveTo(8.4f, 11.2f)
            lineTo(5.0f, 14.6f)
            moveTo(8.4f, 18.0f)
            lineTo(5.0f, 14.6f)
        }
    }

    /** 搜索：一枚镜片。 */
    val Search: ImageVector = inkIcon("Lumo.Search") {
        stroke(1.8f) {
            moveTo(10.6f, 4.6f)
            arcTo(6.0f, 6.0f, 0f, true, true, 10.6f, 16.6f)
            arcTo(6.0f, 6.0f, 0f, true, true, 10.6f, 4.6f)
            close()
        }
        stroke(1.9f) {
            moveTo(15.0f, 15.0f)
            lineTo(19.4f, 19.4f)
        }
    }

    /** 刷新：一段回转的墨圈。 */
    val Refresh: ImageVector = inkIcon("Lumo.Refresh") {
        stroke(1.8f) {
            moveTo(18.8f, 12.0f)
            arcTo(6.8f, 6.8f, 0f, true, true, 15.4f, 6.1f)
        }
        stroke(1.8f) {
            moveTo(18.6f, 4.6f)
            lineTo(18.8f, 8.6f)
            lineTo(14.8f, 8.8f)
        }
    }

    /** 更多：三点垂排。 */
    val More: ImageVector = inkIcon("Lumo.More") {
        solid {
            moveTo(12.0f, 5.2f)
            arcTo(1.3f, 1.3f, 0f, true, true, 12.0f, 7.8f)
            arcTo(1.3f, 1.3f, 0f, true, true, 12.0f, 5.2f)
            close()
            moveTo(12.0f, 10.7f)
            arcTo(1.3f, 1.3f, 0f, true, true, 12.0f, 13.3f)
            arcTo(1.3f, 1.3f, 0f, true, true, 12.0f, 10.7f)
            close()
            moveTo(12.0f, 16.2f)
            arcTo(1.3f, 1.3f, 0f, true, true, 12.0f, 18.8f)
            arcTo(1.3f, 1.3f, 0f, true, true, 12.0f, 16.2f)
            close()
        }
    }

    /** 键盘：一块键位板。 */
    val Keyboard: ImageVector = inkIcon("Lumo.Keyboard") {
        stroke(1.7f) {
            moveTo(6.0f, 6.4f)
            lineTo(18.0f, 6.4f)
            quadTo(20.0f, 6.4f, 20.0f, 8.4f)
            lineTo(20.0f, 15.6f)
            quadTo(20.0f, 17.6f, 18.0f, 17.6f)
            lineTo(6.0f, 17.6f)
            quadTo(4.0f, 17.6f, 4.0f, 15.6f)
            lineTo(4.0f, 8.4f)
            quadTo(4.0f, 6.4f, 6.0f, 6.4f)
            close()
        }
        stroke(1.6f) {
            moveTo(7.6f, 10.4f)
            lineTo(7.6f, 10.4f)
            moveTo(11.0f, 10.4f)
            lineTo(11.0f, 10.4f)
            moveTo(14.4f, 10.4f)
            lineTo(14.4f, 10.4f)
            moveTo(16.6f, 10.4f)
            lineTo(16.6f, 10.4f)
            moveTo(9.0f, 14.0f)
            lineTo(15.0f, 14.0f)
        }
    }

    /** 擂台：一只奖杯。 */
    val Trophy: ImageVector = inkIcon("Lumo.Trophy") {
        stroke(1.7f) {
            moveTo(8.0f, 4.6f)
            lineTo(16.0f, 4.6f)
            lineTo(16.0f, 9.6f)
            quadTo(16.0f, 13.6f, 12.0f, 13.6f)
            quadTo(8.0f, 13.6f, 8.0f, 9.6f)
            close()
        }
        stroke(1.7f) {
            moveTo(8.0f, 6.6f)
            lineTo(5.4f, 6.6f)
            quadTo(5.4f, 10.6f, 8.6f, 10.6f)
            moveTo(16.0f, 6.6f)
            lineTo(18.6f, 6.6f)
            quadTo(18.6f, 10.6f, 15.4f, 10.6f)
        }
        stroke(1.7f) {
            moveTo(12.0f, 13.6f)
            lineTo(12.0f, 16.8f)
            moveTo(8.6f, 19.4f)
            lineTo(15.4f, 19.4f)
            moveTo(10.4f, 16.8f)
            lineTo(13.6f, 16.8f)
        }
    }

    /** 勾：一件事做完了。 */
    val Check: ImageVector = inkIcon("Lumo.Check") {
        stroke(1.9f) {
            moveTo(5.6f, 12.4f)
            lineTo(10.2f, 17.0f)
            lineTo(18.4f, 7.2f)
        }
    }

    /** 空勾选框。 */
    val CheckOff: ImageVector = inkIcon("Lumo.CheckOff") {
        stroke(1.7f) {
            moveTo(6.4f, 6.4f)
            lineTo(17.6f, 6.4f)
            lineTo(17.6f, 17.6f)
            lineTo(6.4f, 17.6f)
            close()
        }
    }

    /** 已勾选框。 */
    val CheckOn: ImageVector = inkIcon("Lumo.CheckOn") {
        stroke(1.7f) {
            moveTo(6.4f, 6.4f)
            lineTo(17.6f, 6.4f)
            lineTo(17.6f, 17.6f)
            lineTo(6.4f, 17.6f)
            close()
        }
        stroke(1.8f) {
            moveTo(8.8f, 12.2f)
            lineTo(11.4f, 14.8f)
            lineTo(15.6f, 9.0f)
        }
    }

    /** 笔：一段笔杆加一枚笔头。 */
    val Brush: ImageVector = inkIcon("Lumo.Brush") {
        stroke(1.7f) {
            moveTo(18.6f, 4.8f)
            lineTo(11.4f, 12.0f)
            lineTo(9.0f, 9.6f)
            lineTo(16.2f, 4.4f)
            close()
        }
        stroke(1.7f) {
            moveTo(9.0f, 9.6f)
            lineTo(7.2f, 15.0f)
            lineTo(5.0f, 19.0f)
            lineTo(9.6f, 17.4f)
            lineTo(11.4f, 12.0f)
        }
    }

    /** 展开：向外的一对折角。 */
    val Expand: ImageVector = inkIcon("Lumo.Expand") {
        stroke(1.8f) {
            moveTo(13.6f, 4.8f)
            lineTo(19.2f, 4.8f)
            lineTo(19.2f, 10.4f)
            moveTo(10.4f, 19.2f)
            lineTo(4.8f, 19.2f)
            lineTo(4.8f, 13.6f)
            moveTo(19.2f, 4.8f)
            lineTo(13.8f, 10.2f)
            moveTo(4.8f, 19.2f)
            lineTo(10.2f, 13.8f)
        }
    }

    /** 收起：向内的一对折角。 */
    val Collapse: ImageVector = inkIcon("Lumo.Collapse") {
        stroke(1.8f) {
            moveTo(19.2f, 10.2f)
            lineTo(13.8f, 10.2f)
            lineTo(13.8f, 4.8f)
            moveTo(4.8f, 13.8f)
            lineTo(10.2f, 13.8f)
            lineTo(10.2f, 19.2f)
            moveTo(19.2f, 4.8f)
            lineTo(13.8f, 10.2f)
            moveTo(4.8f, 19.2f)
            lineTo(10.2f, 13.8f)
        }
    }

    /** 统计：三根墨柱与一条基线。 */
    val Chart: ImageVector = inkIcon("Lumo.Chart") {
        stroke(1.7f) {
            moveTo(4.4f, 19.4f)
            lineTo(19.6f, 19.4f)
        }
        stroke(1.7f) {
            moveTo(8.0f, 19.4f)
            lineTo(8.0f, 12.4f)
            moveTo(12.0f, 19.4f)
            lineTo(12.0f, 7.4f)
            moveTo(16.0f, 19.4f)
            lineTo(16.0f, 10.6f)
        }
    }

    /** 历史：一只怀表。 */
    val Clock: ImageVector = inkIcon("Lumo.Clock") {
        stroke(1.7f) {
            moveTo(12.0f, 4.4f)
            arcTo(7.6f, 7.6f, 0f, true, true, 12.0f, 19.6f)
            arcTo(7.6f, 7.6f, 0f, true, true, 12.0f, 4.4f)
            close()
        }
        stroke(1.7f) {
            moveTo(12.0f, 8.2f)
            lineTo(12.0f, 12.4f)
            lineTo(15.2f, 14.2f)
        }
    }

    /** 文档：一张折角的纸。 */
    val Doc: ImageVector = inkIcon("Lumo.Doc") {
        stroke(1.7f) {
            moveTo(6.2f, 5.6f)
            lineTo(14.0f, 5.6f)
            lineTo(18.0f, 9.6f)
            lineTo(18.0f, 18.4f)
            lineTo(6.2f, 18.4f)
            close()
        }
        stroke(1.6f) {
            moveTo(14.0f, 5.6f)
            lineTo(14.0f, 9.6f)
            lineTo(18.0f, 9.6f)
            moveTo(8.8f, 13.2f)
            lineTo(15.2f, 13.2f)
            moveTo(8.8f, 15.8f)
            lineTo(13.0f, 15.8f)
        }
    }

    /** 代码：一对尖括号与一道斜杠。 */
    val Code: ImageVector = inkIcon("Lumo.Code") {
        stroke(1.8f) {
            moveTo(9.0f, 8.0f)
            lineTo(5.2f, 12.0f)
            lineTo(9.0f, 16.0f)
            moveTo(15.0f, 8.0f)
            lineTo(18.8f, 12.0f)
            lineTo(15.0f, 16.0f)
            moveTo(13.4f, 6.2f)
            lineTo(10.6f, 17.8f)
        }
    }

    /** 闪电：免费 / 极速。 */
    val Bolt: ImageVector = inkIcon("Lumo.Bolt") {
        stroke(1.7f) {
            moveTo(13.6f, 3.8f)
            lineTo(7.2f, 13.2f)
            lineTo(11.4f, 13.2f)
            lineTo(10.4f, 20.2f)
            lineTo(16.8f, 10.8f)
            lineTo(12.6f, 10.8f)
            close()
        }
    }

    /** 归档：一只带盖的匣子。 */
    val Archive: ImageVector = inkIcon("Lumo.Archive") {
        stroke(1.7f) {
            moveTo(4.6f, 7.4f)
            lineTo(19.4f, 7.4f)
            lineTo(19.4f, 10.4f)
            lineTo(4.6f, 10.4f)
            close()
        }
        stroke(1.7f) {
            moveTo(6.2f, 10.4f)
            lineTo(6.2f, 18.0f)
            quadTo(6.2f, 19.4f, 7.6f, 19.4f)
            lineTo(16.4f, 19.4f)
            quadTo(17.8f, 19.4f, 17.8f, 18.0f)
            lineTo(17.8f, 10.4f)
        }
        stroke(1.7f) {
            moveTo(10.0f, 13.8f)
            lineTo(14.0f, 13.8f)
        }
    }

    /** 人：头与肩。 */
    val Person: ImageVector = inkIcon("Lumo.Person") {
        stroke(1.7f) {
            moveTo(12.0f, 5.4f)
            arcTo(3.6f, 3.6f, 0f, true, true, 12.0f, 12.6f)
            arcTo(3.6f, 3.6f, 0f, true, true, 12.0f, 5.4f)
            close()
        }
        stroke(1.7f) {
            moveTo(5.4f, 19.6f)
            quadTo(5.4f, 14.6f, 12.0f, 14.6f)
            quadTo(18.6f, 14.6f, 18.6f, 19.6f)
        }
    }

    // ---- 构件 ----

    private fun inkIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** 一块墨色：实心填充，颜色同样留给 `Icon(tint=)`。 */
    private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathBuilder = block,
        )
    }

    /** 一条墨线：只描边，颜色留给 `Icon(tint=)`。 */
    private fun ImageVector.Builder.stroke(
        width: Float,
        block: PathBuilder.() -> Unit,
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }
}
