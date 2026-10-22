# HCtool 玻璃组件统一规范（Liquid Glass UI Spec）

> 本规范以右下角**添加键 `GlassFab`** 为视觉与交互模板。所有玻璃质感的组件（按钮、面板、卡片、输入框背景等）都必须走统一入口，**禁止**各自复制 `drawBackdrop` 调用链，防止参数漂移导致视觉不一致。

## 一、唯一出处

| 用途 | API | 文件 |
|---|---|---|
| 玻璃**材质**（可点/不可点都能用） | `Modifier.glassSurface(...)` | `ui/components/GlassSurface.kt` |
| 玻璃**按钮**（材质 + 按压反馈 + 点击） | `GlassButton(...)` | `ui/components/GlassSurface.kt` |

材质参数只在 `glassSurface` 一处定义，改这里即全局生效。

### 材质参数（模板值，勿在别处硬编码）
- 效果：`vibrancy()` + `blur(5.dp)` + `lens(20.dp, 36.dp)`
- 高光：`Highlight.Default`
- 阴影：`Shadow(radius = 32.dp, color = Color.Black.copy(alpha = 0.2f))`
- 形状：默认 `Capsule()`；**会向下展开的面板**用 `RoundedCornerShape(32.dp)`（收起时正好是胶囊，展开时是圆角矩形）
- 表面叠色：默认 `Color.Transparent`（纯净玻璃）；需要提高文字对比度时才传 `surfaceTint`
- 预览态（layoutlib 不支持 RenderEffect）：退化为 `Color.White.copy(alpha = 0.12f)` 纯色

### 按压反馈参数（模板值）
- 按下缩放：`0.94f`
- 弹簧：`spring(dampingRatio = 0.65f, stiffness = 900f)`
- 实现：命名 `MutableInteractionSource` + `collectIsPressedAsState()` + `graphicsLayer { scaleX/scaleY }`

## 二、使用模式

### 1. 可点击的玻璃组件 → `GlassButton`
搜索键、添加键、标题框等一切"按了有反应"的玻璃元素。

```kotlin
GlassButton(
    backdrop = imageBackdrop,          // 采样源按层级选：顶层浮层 imageBackdrop，底栏/FAB backdrop
    onClick = { /* 动作 */ },
    modifier = Modifier.size(64.dp),   // 尺寸、对齐等放这里
    // shape = RoundedCornerShape(32.dp),   // 需要向下展开时才传
) {
    // 图标 / 文字 / Column 等内容
}
```

### 2. 不可点的玻璃面板/条 → `Modifier.glassSurface`
卡片背景、输入框玻璃底、装饰条等。

```kotlin
Box(
    modifier = Modifier
        .glassSurface(
            backdrop = imageBackdrop,
            shape = searchShape,
            inPreview = inPreview
        )
) { /* 内容 */ }
```

## 三、采样源（backdrop）怎么选
- `imageBackdrop`：只含背景图的采样源，**顶层浮层**（标题框、搜索键、搜索输入框）用它，避免把标题文字自己采进去出现重影。
- `backdrop`：含页面内容的采样源，**底栏 / FAB** 用它。
- `glassBackdrop`：miuix-blur 那一套（`FloatingBottomBar` 专用），与 kyant 的 `glassSurface` 不是同一条管线，勿混用。

## 四、已迁移组件清单
- `GlassFab.kt`：重构为 `GlassButton` 的薄封装，公开 API 不变。
- `MainActivity.kt` 顶栏：标题框（可点 + 预留向下展开面板）、搜索按钮、搜索输入框，均统一到 `glassSurface` / `GlassButton`。

## 五、新增组件检查清单
1. 是否可用 `GlassButton`（可点）或 `glassSurface`（不可点）覆盖？→ 优先复用。
2. 采样源选对了吗（`imageBackdrop` vs `backdrop`）？
3. 需要向下展开吗？→ 形状用 `RoundedCornerShape(32.dp)` 而非 `Capsule()`。
4. **不要**新写 `drawBackdrop`；确需特殊材质，先考虑给 `glassSurface` 加参数。

> 备注：IDE 有时误报 `Unresolved reference 'Capsule'`（`com.kyant.shapes.Capsule`），属索引问题，Gradle 编译不受影响。
