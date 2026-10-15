# YoTheme 组件调用接口示例

本文档提供了 YoTheme 库中所有具体组件的调用接口示例和常见用法。

| 项目 | 内容         |
| --- |------------|
| 版本号 | V1.0.1 |
| 修改日期 | 2026/01/22 |
| 编写人员 | 杨庭锋 |

---

## 目录

1. [YoButton (按钮)](#yobutton-按钮)
2. [YoSegmentButton (分段按钮)](#yosegmentbutton-分段按钮)
3. [YoRadio (单选框)](#yoradio-单选框)
4. [YoCheckbox (多选框)](#yocheckbox-多选框)
5. [YoSwitch (开关)](#yoswitch-开关)
6. [YoSlider (滑块)](#yoslider-滑块)
7. [YoIconTextCard (图标文本卡片)](#yoicontextcard-图标文本卡片)
8. [YoList (列表)](#yolist-列表)
9. [YoMenu (菜单)](#yomenu-菜单)
10. [YoNavigationBar (导航栏)](#yonavigationbar-导航栏)
11. [YoScrollBar (滚动条)](#yoscrollbar-滚动条)
12. [YoModalPanel (模态面板)](#yomodalpanel-模态面板)
13. [YoDialog (对话框)](#yodialog-对话框)

---

## YoButton (按钮)

### 简介
自定义Yo按钮组件，提供风格一致的按钮样式，包含样式和背景定义。不依赖任何第三方库，纯原生实现。

### 包名
```
com.yovo.yotheme.components.yo_button.YoButton
```

### 类名
- `YoButton` - 主按钮类

### 基本用法

#### 1. 创建简单按钮

```java
import com.yovo.yotheme.components.yo_button.YoButton;

// 在 Activity 中
YoButton button = YoButton.create(this);
button.setText("点击我");
container.addView(button);

// 或者在 XML 中使用
// <com.yovo.yotheme.components.yo_button.YoButton
//     android:id="@+id/yo_button"
//     android:layout_width="wrap_content"
//     android:layout_height="wrap_content"
//     android:text="按钮文本" />
```

#### 2. 创建带文本的按钮

```java
YoButton button = YoButton.create(this, "确定");
container.addView(button);
```

#### 3. 设置按钮点击监听

```java
YoButton button = YoButton.create(this, "提交");
button.setOnClickListener(v -> {
    // 处理按钮点击事件
    Toast.makeText(this, "按钮被点击了", Toast.LENGTH_SHORT).show();
});
container.addView(button);
```

#### 4. 动态修改按钮样式

```java
YoButton button = YoButton.create(this, "设置样式");

// 修改文本
button.setText("新文本");

// 修改文本大小
button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

// 修改文本颜色
button.setTextColor(ColorMapper.map(YoThemeColor.WHITE));

container.addView(button);
```

#### 5. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.components.yo_button.YoButton
    android:id="@+id/yo_button"
    android:layout_width="200dp"
    android:layout_height="44dp"
    android:text="按钮"
    android:gravity="center"
    android:textSize="16sp" />
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `create(Context)` | 创建一个新的 YoButton 实例 |
| `create(Context, String)` | 创建一个带文本的 YoButton 实例 |
| `setText(CharSequence)` | 设置按钮文本 |
| `setTextColor(int)` | 设置文本颜色 |
| `setTextSize(int, float)` | 设置文本大小 |
| `setOnClickListener(OnClickListener)` | 设置点击监听器 |

#### 样式参考

![image-20260122093327779](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093327779.png)

---

## YoSegmentButton (分段按钮)

### 简介
分段按钮组件，提供分段选择功能。参考 HarmonyOS 设计风格，采用 32dp 圆角弧度设计，支持左右滑动动画切换，无点击震动和水波纹效果。

### 包名
```
com.yovo.yotheme.components.yo_button.YoSegmentButton
```

### 类名
- `YoSegmentButton` - 主分段按钮类

### 基本用法

#### 1. 创建简单分段按钮

```java
import com.yovo.yotheme.components.yo_button.YoSegmentButton;
import java.util.Arrays;

// 在 Activity 中
YoSegmentButton segmentButton = new YoSegmentButton(this);

// 设置选项列表
segmentButton.setItems(Arrays.asList("选项1", "选项2", "选项3"));

container.addView(segmentButton);
```

#### 2. 设置分段按钮选中监听

```java
YoSegmentButton segmentButton = new YoSegmentButton(this);

// 设置选项变化监听器
segmentButton.setOnSegmentSelectedListener((index, item) -> {
    Toast.makeText(this, "选中: " + item + " (索引: " + index + ")", Toast.LENGTH_SHORT).show();
});

segmentButton.setItems(Arrays.asList("日", "周", "月", "年"));

container.addView(segmentButton);
```

#### 3. 获取当前选中项

```java
YoSegmentButton segmentButton = new YoSegmentButton(this);
segmentButton.setItems(Arrays.asList("高", "中", "低"));

// 获取当前选中索引
int selectedIndex = segmentButton.getSelectedIndex();

// 获取当前选中的文本
String selectedItem = segmentButton.getSelectedItem();

container.addView(segmentButton);
```

#### 4. 编程方式选择项

```java
YoSegmentButton segmentButton = new YoSegmentButton(this);
segmentButton.setItems(Arrays.asList("项目A", "项目B", "项目C"));

// 通过代码选择第二个选项（索引为1）
segmentButton.selectItem(1);

container.addView(segmentButton);
```

#### 5. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.components.yo_button.YoSegmentButton
    android:id="@+id/yo_segment_button"
    android:layout_width="match_parent"
    android:layout_height="44dp"
    android:layout_margin="16dp" />
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `setItems(List<String>)` | 设置分段按钮的选项列表 |
| `selectItem(int)` | 以编程方式选择指定索引的选项 |
| `getSelectedIndex()` | 获取当前选中的索引 |
| `getSelectedItem()` | 获取当前选中的选项文本 |
| `setOnSegmentSelectedListener(OnSegmentSelectedListener)` | 设置选项变化监听器 |

### 组件特性

- **圆角设计**：采用 32dp 圆角半径，符合 HarmonyOS 设计规范
- **平滑动画**：指示器滑动采用平滑动画，动画时长 250ms
- **文字动画**：选中/未选中状态文字具有颜色变化、缩放和粗细变化动画
- **高度固定**：默认高度 44dp，自适应调整
- **背景色**：使用 `COMP_BACKGROUND_SECONDARY` 作为背景
- **指示器色**：选中指示器使用 `COMP_BACKGROUND_PRIMARY` 颜色
- **无波纹效果**：不包含点击水波纹和震动反馈

#### 样式参考

![image-20260122093338115](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093338115.png)

---

## YoRadio (单选框)

### 简介
单选框组件，提供圆形单选按钮。未选中时为灰色边框圆形，选中时为蓝色填充圆形且中间有白色对勾。默认大小 24dp。

### 包名
```
com.yovo.yotheme.small_widget.YoRadio
```

### 类名
- `YoRadio` - 主单选框类

### 基本用法

#### 1. 创建单选框

```java
import com.yovo.yotheme.small_widget.YoRadio;

// 在 Activity 中
YoRadio radio = new YoRadio(this);
container.addView(radio);
```

#### 2. 创建带初始状态的单选框

```java
YoRadio radio = new YoRadio(this);

// 设置为选中状态
radio.setChecked(true);

container.addView(radio);
```

#### 3. 设置单选框状态变化监听

```java
YoRadio radio1 = new YoRadio(this);
YoRadio radio2 = new YoRadio(this);
YoRadio radio3 = new YoRadio(this);

// 设置状态变化监听器
YoRadio.OnCheckedChangeListener listener = (radio, isChecked) -> {
    if (isChecked) {
        // 如果此单选框被选中，取消其他单选框的选中状态
        if (radio == radio1) {
            radio2.setChecked(false);
            radio3.setChecked(false);
            Toast.makeText(this, "选中: 选项 1", Toast.LENGTH_SHORT).show();
        } else if (radio == radio2) {
            radio1.setChecked(false);
            radio3.setChecked(false);
            Toast.makeText(this, "选中: 选项 2", Toast.LENGTH_SHORT).show();
        } else if (radio == radio3) {
            radio1.setChecked(false);
            radio2.setChecked(false);
            Toast.makeText(this, "选中: 选项 3", Toast.LENGTH_SHORT).show();
        }
    }
};

radio1.setChecked(true);
radio1.setOnCheckedChangeListener(listener);
radio2.setOnCheckedChangeListener(listener);
radio3.setOnCheckedChangeListener(listener);

// 添加到容器
LinearLayout radioGroup = new LinearLayout(this);
radioGroup.setOrientation(LinearLayout.HORIZONTAL);
radioGroup.addView(radio1);
radioGroup.addView(radio2);
radioGroup.addView(radio3);
container.addView(radioGroup);
```

#### 4. 获取单选框状态

```java
YoRadio radio = new YoRadio(this);

// 获取当前选中状态
boolean isChecked = radio.isChecked();

// 切换选中状态
radio.toggle();

container.addView(radio);
```

#### 5. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.small_widget.YoRadio
    android:id="@+id/yo_radio"
    android:layout_width="24dp"
    android:layout_height="24dp"
    android:layout_margin="8dp" />
```

### 主要方法

| 方法 | 描述 |
| --- | --- |
| `setChecked(boolean)` | 设置单选框的选中状态 |
| `isChecked()` | 获取单选框的选中状态 |
| `toggle()` | 切换单选框的选中状态 |
| `setOnCheckedChangeListener(OnCheckedChangeListener)` | 设置状态变化监听器 |

### 组件特性

- **圆形设计**：圆形单选按钮，易于识别
- **默认大小**：24dp × 24dp
- **颜色设置**：未选中为灰色，选中为品牌蓝色
- **对勾标记**：选中时显示白色对勾
- **自动切换**：点击时自动切换选中状态

#### 样式参考

![image-20260122093357756](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093357756.png)

---

## YoCheckbox (多选框)

### 简介
多选框组件，提供圆角矩形多选按钮。采用 8dp 圆角设计。未选中时为灰色边框圆角矩形，选中时为蓝色填充圆角矩形且中间有白色对勾。默认大小 24dp。

### 包名
```
com.yovo.yotheme.small_widget.YoCheckbox
```

### 类名
- `YoCheckbox` - 主多选框类

### 基本用法

#### 1. 创建多选框

```java
import com.yovo.yotheme.small_widget.YoCheckbox;

// 在 Activity 中
YoCheckbox checkbox = new YoCheckbox(this);
container.addView(checkbox);
```

#### 2. 创建带初始状态的多选框

```java
YoCheckbox checkbox = new YoCheckbox(this);

// 设置为选中状态
checkbox.setChecked(true);

container.addView(checkbox);
```

#### 3. 设置多选框状态变化监听

```java
YoCheckbox checkbox1 = new YoCheckbox(this);
YoCheckbox checkbox2 = new YoCheckbox(this);
YoCheckbox checkbox3 = new YoCheckbox(this);

// 设置状态变化监听器
YoCheckbox.OnCheckedChangeListener listener = (checkbox, isChecked) -> {
    String status = isChecked ? "选中" : "未选中";
    if (checkbox == checkbox1) {
        Toast.makeText(this, "选项 A: " + status, Toast.LENGTH_SHORT).show();
    } else if (checkbox == checkbox2) {
        Toast.makeText(this, "选项 B: " + status, Toast.LENGTH_SHORT).show();
    } else if (checkbox == checkbox3) {
        Toast.makeText(this, "选项 C: " + status, Toast.LENGTH_SHORT).show();
    }
};

checkbox1.setOnCheckedChangeListener(listener);
checkbox2.setOnCheckedChangeListener(listener);
checkbox3.setOnCheckedChangeListener(listener);

// 添加到容器
LinearLayout checkboxGroup = new LinearLayout(this);
checkboxGroup.setOrientation(LinearLayout.HORIZONTAL);
checkboxGroup.addView(checkbox1);
checkboxGroup.addView(checkbox2);
checkboxGroup.addView(checkbox3);
container.addView(checkboxGroup);
```

#### 4. 获取多选框状态

```java
YoCheckbox checkbox = new YoCheckbox(this);

// 获取当前选中状态
boolean isChecked = checkbox.isChecked();

// 切换选中状态
checkbox.toggle();

container.addView(checkbox);
```

#### 5. 处理多个选中项

```java
YoCheckbox[] checkboxes = new YoCheckbox[3];
String[] options = {"选项1", "选项2", "选项3"};

// 初始化多选框
for (int i = 0; i < 3; i++) {
    checkboxes[i] = new YoCheckbox(this);
    final int index = i;
    checkboxes[i].setOnCheckedChangeListener((cb, isChecked) -> {
        if (isChecked) {
            Toast.makeText(this, options[index] + " 已选中", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, options[index] + " 已取消", Toast.LENGTH_SHORT).show();
        }
    });
}

// 获取所有选中项
List<String> selectedItems = new ArrayList<>();
for (int i = 0; i < checkboxes.length; i++) {
    if (checkboxes[i].isChecked()) {
        selectedItems.add(options[i]);
    }
}

container.addView(checkboxes[0]);
container.addView(checkboxes[1]);
container.addView(checkboxes[2]);
```

#### 6. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.small_widget.YoCheckbox
    android:id="@+id/yo_checkbox"
    android:layout_width="24dp"
    android:layout_height="24dp"
    android:layout_margin="8dp" />
```

### 主要方法

| 方法 | 描述 |
| --- | --- |
| `setChecked(boolean)` | 设置多选框的选中状态 |
| `isChecked()` | 获取多选框的选中状态 |
| `toggle()` | 切换多选框的选中状态 |
| `setOnCheckedChangeListener(OnCheckedChangeListener)` | 设置状态变化监听器 |

### 组件特性

- **圆角矩形设计**：8dp 圆角，美观现代
- **默认大小**：24dp × 24dp
- **颜色设置**：未选中为灰色，选中为品牌蓝色
- **对勾标记**：选中时显示白色对勾
- **自动切换**：点击时自动切换选中状态
- **支持多选**：可同时选中多个选项

#### 样式参考

![image-20260122093409281](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093409281.png)

---

## YoSwitch (开关)

### 简介
自定义滑动开关控件，采用灰色背景，滑动部分为宇宙蓝，滑块为白色的风格设计。支持滑块上下边界与滑轨平齐的一体化设计。

### 包名
```
com.yovo.yotheme.components.yo_switch.YoSwitch
```

### 类名
- `YoSwitch` - 主开关类

### 基本用法

#### 1. 创建简单开关

```java
import com.yovo.yotheme.components.yo_switch.YoSwitch;

// 在 Activity 中
YoSwitch yoSwitch = YoSwitch.create(this);
container.addView(yoSwitch);
```

#### 2. 创建带初始状态的开关

```java
// 创建初始状态为打开的开关
YoSwitch yoSwitch = YoSwitch.create(this, true);
container.addView(yoSwitch);

// 创建初始状态为关闭的开关
YoSwitch yoSwitch = YoSwitch.create(this, false);
container.addView(yoSwitch);
```

#### 3. 设置开关变化监听

```java
YoSwitch yoSwitch = YoSwitch.create(this);

// 设置开关状态变化监听器
yoSwitch.setOnCheckedChangeListener((switch_, isChecked) -> {
    if (isChecked) {
        Toast.makeText(this, "开关已打开", Toast.LENGTH_SHORT).show();
    } else {
        Toast.makeText(this, "开关已关闭", Toast.LENGTH_SHORT).show();
    }
});

container.addView(yoSwitch);
```

#### 4. 动态获取和设置状态

```java
YoSwitch yoSwitch = YoSwitch.create(this);

// 获取当前状态
boolean isChecked = yoSwitch.isChecked();

// 设置状态（不触发监听器）
yoSwitch.setChecked(true, false);

// 设置状态（触发监听器）
yoSwitch.setChecked(false, true);

container.addView(yoSwitch);
```

#### 5. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.components.yo_switch.YoSwitch
    android:id="@+id/yo_switch"
    android:layout_width="50dp"
    android:layout_height="25dp"
    android:layout_margin="16dp" />
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `create(Context)` | 创建一个新的 YoSwitch 实例 |
| `create(Context, boolean)` | 创建一个带初始状态的 YoSwitch 实例 |
| `isChecked()` | 获取当前开关状态 |
| `setChecked(boolean, boolean)` | 设置开关状态 (第二个参数表示是否触发监听器) |
| `setOnCheckedChangeListener(OnCheckedChangeListener)` | 设置状态变化监听器 |

#### 样式参考

![image-20260122093418039](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093418039.png)

---

## YoSlider (滑块)

### 简介
自定义滑动条组件，采用鸿蒙风格设计。纯UI组件，符合MVVM架构的View层职责。支持拖拽、点击跳转、键盘导航和无障碍访问。

### 包名
```
com.yovo.yotheme.components.yo_slider.YoSlider
```

### 类名
- `YoSlider` - 主滑块类
- `YoSliderBuilder` - 滑块构建器
- `YoSliderInterface` - 滑块接口

### 基本用法

#### 1. 创建简单滑块

```java
import com.yovo.yotheme.components.yo_slider.YoSlider;

// 在 Activity 中
YoSlider slider = new YoSlider(this);
slider.setLayoutParams(new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    dpToPx(50)
));
container.addView(slider);
```

#### 2. 配置滑块参数

```java
YoSlider slider = new YoSlider(this);

// 设置最小值、最大值和当前值
slider.setMinValue(0);
slider.setMaxValue(100);
slider.setCurrentValue(50, false); // false 表示不触发监听器

slider.setLayoutParams(new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    dpToPx(50)
));
container.addView(slider);
```

#### 3. 设置滑块变化监听

```java
YoSlider slider = new YoSlider(this);
slider.setMinValue(0);
slider.setMaxValue(100);

// 设置滑块变化监听器
slider.setOnSliderChangeListener(new YoSlider.OnSliderChangeListener() {
    @Override
    public void onStartTrackingTouch(YoSlider slider, int value, boolean fromUser) {
        // 开始拖拽
        Log.d("YoSlider", "开始拖拽，值: " + value);
    }
    
    @Override
    public void onValueChanged(YoSlider slider, int value, boolean fromUser) {
        // 值改变
        Log.d("YoSlider", "值改变: " + value);
    }
    
    @Override
    public void onStopTrackingTouch(YoSlider slider, int value, boolean fromUser) {
        // 停止拖拽
        Log.d("YoSlider", "停止拖拽，最终值: " + value);
    }
});

slider.setLayoutParams(new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    dpToPx(50)
));
container.addView(slider);
```

#### 4. 使用 Builder 创建

```java
YoSlider slider = YoSliderBuilder.create(this)
    .setMinValue(0)
    .setMaxValue(100)
    .setCurrentValue(50)
    .setAnimationEnabled(true)
    .setOnSliderChangeListener(new YoSlider.OnSliderChangeListener() {
        @Override
        public void onStartTrackingTouch(YoSlider slider, int value, boolean fromUser) {}
        
        @Override
        public void onValueChanged(YoSlider slider, int value, boolean fromUser) {
            Log.d("YoSlider", "当前值: " + value);
        }
        
        @Override
        public void onStopTrackingTouch(YoSlider slider, int value, boolean fromUser) {}
    })
    .build();

container.addView(slider);
```

#### 5. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.components.yo_slider.YoSlider
    android:id="@+id/yo_slider"
    android:layout_width="match_parent"
    android:layout_height="50dp"
    android:layout_margin="16dp" />
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `setMinValue(int)` | 设置最小值 |
| `setMaxValue(int)` | 设置最大值 |
| `getCurrentValue()` | 获取当前值 |
| `setCurrentValue(int, boolean)` | 设置当前值 |
| `setOnSliderChangeListener(OnSliderChangeListener)` | 设置值变化监听器 |
| `setAnimationEnabled(boolean)` | 启用/禁用动画 |

#### 样式参考

![image-20260122093428773](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093428773.png)

---

## YoIconTextCard (图标文本卡片)

### 简介
通用图标+主/副标题卡片组件，适用于首页眼镜功能卡、AI功能卡等左图右文场景。

### 包名
```
com.yovo.yotheme.components.yo_card.YoIconTextCard
```

### 类名
- `YoIconTextCard` - 主卡片类
- `IconActionButtonCard` - 图标按钮卡片

### 基本用法

#### 1. 创建简单卡片

```java
import com.yovo.yotheme.components.yo_card.YoIconTextCard;

// 在 Activity 中
YoIconTextCard card = new YoIconTextCard(this);
card.setTitle("卡片标题");
card.setSubtitle("卡片副标题");
card.setIcon(R.drawable.ic_icon);

container.addView(card);
```

#### 2. 设置卡片内容

```java
YoIconTextCard card = new YoIconTextCard(this);

// 设置标题和副标题
card.setTitle("功能名称");
card.setSubtitle("功能描述");

// 设置图标
card.setIcon(R.drawable.ic_feature);

// 设置标题颜色
card.setTitleColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));

// 设置副标题颜色
card.setSubtitleColor(ColorMapper.map(YoThemeColor.FONT_SECONDARY));

// 设置背景颜色
card.setBackgroundColor(ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY));

container.addView(card);
```

#### 3. 设置卡片点击监听

```java
YoIconTextCard card = new YoIconTextCard(this);
card.setTitle("可点击卡片");
card.setSubtitle("点击进入详情");
card.setIcon(R.drawable.ic_arrow);

// 设置点击监听
card.setOnClickListener(v -> {
    Toast.makeText(this, "卡片被点击了", Toast.LENGTH_SHORT).show();
    // 进入详情页面
    startActivity(new Intent(this, DetailActivity.class));
});

container.addView(card);
```

#### 4. 自定义卡片样式

```java
YoIconTextCard card = new YoIconTextCard(this);

// 设置圆角半径
card.setCornerRadius(24f);

// 设置水波纹效果
card.setRippleEnabled(true);

// 设置图标大小
card.setIconSize(40);

// 设置内边距
card.setPadding(16, 12, 16, 12);

card.setTitle("自定义卡片");
card.setSubtitle("自定义样式");
card.setIcon(R.drawable.ic_custom);

container.addView(card);
```

#### 5. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.components.yo_card.YoIconTextCard
    android:id="@+id/yo_card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="16dp" />
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `setTitle(String)` | 设置标题 |
| `setSubtitle(String)` | 设置副标题 |
| `setIcon(int)` | 设置图标 (资源ID) |
| `setIcon(Drawable)` | 设置图标 (Drawable) |
| `setTitleColor(int)` | 设置标题颜色 |
| `setSubtitleColor(int)` | 设置副标题颜色 |
| `setBackgroundColor(int)` | 设置背景颜色 |
| `setRippleEnabled(boolean)` | 启用/禁用水波纹效果 |
| `setCornerRadius(float)` | 设置圆角半径 (dp) |
| `setIconSize(int)` | 设置图标大小 (px) |

#### 样式参考

![image-20260122093501410](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093501410.png)

---

## YoList (列表)

### 简介
YoList 列表组件，统一对外接口，直接通过 YoList 进行调用。支持多种列表项类型，包括左侧图标/图片、右侧按钮/开关等。

### 包名
```
com.yovo.yotheme.components.yo_list.YoList
```

### 类名
- `YoList` - 主列表类
- `YoListBuilder` - 列表构建器
- `YoListInterface` - 列表接口
- `ListCard` - 列表卡片

### 基本用法

#### 1. 创建简单列表

```java
import com.yovo.yotheme.components.yo_list.YoList;

// 在 Activity 中
YoList list = new YoList(this);

// 添加列表项
YoList.ItemConfig item1 = new YoList.ItemConfig(1, "列表项1");
list.addItem(item1);

YoList.ItemConfig item2 = new YoList.ItemConfig(2, "列表项2");
list.addItem(item2);

container.addView(list);
```

#### 2. 添加左侧图标的列表项

```java
YoList list = new YoList(this);

// 添加带图标的列表项
YoList.ItemConfig item = new YoList.ItemConfig(1, "设置")
    .setLeading(YoList.LeadingType.ICON, R.drawable.ic_settings);

list.addItem(item);
container.addView(list);
```

#### 3. 添加副标题和多行副标题

```java
YoList list = new YoList(this);

// 单行副标题
YoList.ItemConfig item1 = new YoList.ItemConfig(1, "用户名")
    .setSubtitle("用户的详细信息");

// 多行副标题
List<String> subtitles = new ArrayList<>();
subtitles.add("第一行副标题");
subtitles.add("第二行副标题");
subtitles.add("第三行副标题");

YoList.ItemConfig item2 = new YoList.ItemConfig(2, "详细信息")
    .setSubtitles(subtitles);

list.addItem(item1);
list.addItem(item2);
container.addView(list);
```

#### 4. 添加右侧内容

```java
YoList list = new YoList(this);

// 右侧文本
YoList.ItemConfig item1 = new YoList.ItemConfig(1, "版本号")
    .setTrailing(YoList.TrailingType.TEXT)
    .setTrailingText("1.0.0");

// 右侧按钮
YoList.ItemConfig item2 = new YoList.ItemConfig(2, "操作")
    .setTrailing(YoList.TrailingType.BUTTON);

// 右侧开关
YoList.ItemConfig item3 = new YoList.ItemConfig(3, "启用功能")
    .setTrailing(YoList.TrailingType.SWITCH);

// 右侧箭头
YoList.ItemConfig item4 = new YoList.ItemConfig(4, "进入详情")
    .setTrailing(YoList.TrailingType.ARROW_RIGHT);

list.addItem(item1);
list.addItem(item2);
list.addItem(item3);
list.addItem(item4);
container.addView(list);
```

#### 5. 设置列表项点击监听

```java
YoList list = new YoList(this);

// 设置列表项点击监听
list.setOnItemClickListener((itemId, view) -> {
    Toast.makeText(this, "列表项 " + itemId + " 被点击了", Toast.LENGTH_SHORT).show();
});

YoList.ItemConfig item = new YoList.ItemConfig(1, "可点击项");
list.addItem(item);

container.addView(list);
```

#### 6. 使用 Builder 创建

```java
YoList list = YoListBuilder.create(this)
    .addItem(1, "项目1", YoList.LeadingType.ICON, R.drawable.ic_icon1)
    .addItem(2, "项目2", YoList.LeadingType.IMAGE, R.drawable.ic_image)
    .setOnItemClickListener((itemId, view) -> {
        Log.d("YoList", "点击项: " + itemId);
    })
    .build();

container.addView(list);
```

### LeadingType 枚举

| 值 | 描述 |
|----|------|
| `NONE` | 无图标 |
| `ICON` | 图标 |
| `IMAGE` | 图片 |
| `AVATAR` | 头像 |

### TrailingType 枚举

| 值 | 描述 |
|----|------|
| `NONE` | 无内容 |
| `ICON` | 图标 |
| `BUTTON` | 按钮 |
| `SWITCH` | 开关 |
| `TEXT` | 文本 |
| `ICON_TEXT` | 图标+文本 |
| `BUTTON_TEXT` | 按钮+文本 |
| `SWITCH_TEXT` | 开关+文本 |
| `ICON_BUTTON_TEXT` | 图标+按钮+文本 |
| `ARROW_RIGHT` | 右箭头 |
| `CUSTOM` | 自定义View |

### 主要方法

| 方法 | 描述 |
|------|------|
| `addItem(ItemConfig)` | 添加列表项 |
| `removeItem(int)` | 移除列表项 |
| `updateItem(ItemConfig)` | 更新列表项 |
| `setOnItemClickListener(OnItemClickListener)` | 设置列表项点击监听 |
| `setDividerEnabled(boolean)` | 启用/禁用分割线 |

#### 样式参考

![image-20260122093533643](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093533643.png)

---

## YoMenu (菜单)

### 简介
YoMenu 菜单主容器组件，提供菜单管理、外观设置、事件监听等功能，支持滑动。

### 包名
```
com.yovo.yotheme.components.yo_menu.YoMenu
```

### 类名
- `YoMenu` - 主菜单类
- `YoMenuBuilder` - 菜单构建器
- `YoMenuInterface` - 菜单接口
- `YoMenuPopup` - 菜单弹窗

### 基本用法

#### 1. 创建简单菜单

```java
import com.yovo.yotheme.components.yo_menu.YoMenu;

// 在 Activity 中
YoMenu menu = YoMenu.create(this);

// 添加菜单项
menu.addMenuItem(1, "选项1");
menu.addMenuItem(2, "选项2");
menu.addMenuItem(3, "选项3");

container.addView(menu);
```

#### 2. 使用 Builder 创建菜单

```java
YoMenu menu = YoMenuBuilder.createStandard(this)
    .addItem("编辑")
    .addItem("删除")
    .addItem("分享")
    .setOnMenuItemClickListener((itemId) -> {
        switch(itemId) {
            case 1:
                Toast.makeText(this, "编辑", Toast.LENGTH_SHORT).show();
                break;
            case 2:
                Toast.makeText(this, "删除", Toast.LENGTH_SHORT).show();
                break;
            case 3:
                Toast.makeText(this, "分享", Toast.LENGTH_SHORT).show();
                break;
        }
        return true;
    })
    .build();

container.addView(menu);
```

#### 3. 设置菜单项点击监听

```java
YoMenu menu = YoMenu.create(this);

// 设置菜单项点击监听
menu.setOnMenuItemClickListener((itemId) -> {
    Log.d("YoMenu", "菜单项 " + itemId + " 被点击了");
    return true;
});

menu.addMenuItem(1, "第一项");
menu.addMenuItem(2, "第二项");

container.addView(menu);
```

#### 4. 配置菜单外观

```java
YoMenu menu = YoMenu.create(this);

// 显示/隐藏分割线
menu.setShowDivider(true);

// 设置项目内边距
menu.setItemPaddingVertical(16);

// 显示/隐藏滚动条
menu.setShowScrollBar(true);

menu.addMenuItem(1, "项目1");
menu.addMenuItem(2, "项目2");

container.addView(menu);
```

#### 5. 菜单弹窗用法

```java
// 在需要显示菜单的地方（例如长按事件）
View targetView = findViewById(R.id.target_view);

YoMenuPopup menuPopup = new YoMenuPopup(this, targetView);
menuPopup.addMenuItem(1, "复制");
menuPopup.addMenuItem(2, "粘贴");
menuPopup.addMenuItem(3, "删除");

menuPopup.setOnMenuItemClickListener((itemId) -> {
    switch(itemId) {
        case 1:
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            break;
        // ...处理其他项
    }
    menuPopup.dismiss();
    return true;
});

menuPopup.show();
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `create(Context)` | 创建一个新的 YoMenu 实例 |
| `builder(Context)` | 创建菜单构建器 |
| `addMenuItem(int, String)` | 添加菜单项 |
| `removeMenuItem(int)` | 移除菜单项 |
| `setOnMenuItemClickListener(OnMenuItemClickListener)` | 设置菜单项点击监听 |
| `setShowDivider(boolean)` | 设置是否显示分割线 |
| `setItemPaddingVertical(int)` | 设置项目垂直内边距 |
| `setShowScrollBar(boolean)` | 设置是否显示滚动条 |

#### 样式参考

![image-20260122093650878](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093650878.png)

---

## YoNavigationBar (导航栏)

### 简介
导航栏核心组件，提供底部导航栏功能。支持多个导航项，可自定义样式和交互。

### 包名
```
com.yovo.yotheme.components.yo_navigation.YoNavigationBar
```

### 类名
- `YoNavigationBar` - 主导航栏类

### 基本用法

#### 1. 创建导航栏

```java
import com.yovo.yotheme.components.yo_navigation.YoNavigationBar;

// 在 Activity 中
YoNavigationBar navBar = new YoNavigationBar(this);

// 添加导航项
navBar.addNavigationItem(1, "首页", R.drawable.ic_home);
navBar.addNavigationItem(2, "分类", R.drawable.ic_category);
navBar.addNavigationItem(3, "我的", R.drawable.ic_profile);

container.addView(navBar);
```

#### 2. 设置导航项选中监听

```java
YoNavigationBar navBar = new YoNavigationBar(this);

// 设置项目选中监听
navBar.setOnItemSelectedListener((itemId) -> {
    switch(itemId) {
        case 1:
            loadFragment(HomeFragment.class);
            break;
        case 2:
            loadFragment(CategoryFragment.class);
            break;
        case 3:
            loadFragment(ProfileFragment.class);
            break;
    }
    return true;
});

// 添加导航项
navBar.addNavigationItem(1, "首页", R.drawable.ic_home);
navBar.addNavigationItem(2, "分类", R.drawable.ic_category);
navBar.addNavigationItem(3, "我的", R.drawable.ic_profile);

container.addView(navBar);
```

#### 3. 设置重复选中监听

```java
YoNavigationBar navBar = new YoNavigationBar(this);

// 设置重复选中监听（用户重复点击同一项时触发）
navBar.setOnItemReselectedListener((itemId) -> {
    // 例如：滚动到列表顶部
    Log.d("YoNavigationBar", "重复选中项: " + itemId);
});

navBar.addNavigationItem(1, "首页", R.drawable.ic_home);
navBar.addNavigationItem(2, "分类", R.drawable.ic_category);

container.addView(navBar);
```

#### 4. 配置导航栏样式

```java
YoNavigationBar navBar = new YoNavigationBar(this);

// 设置导航栏高度
navBar.setNavigationHeight(56);

// 设置最大项目数
navBar.setMaxItemCount(5);

// 配置分割线
navBar.setDivider(true, ColorMapper.map(YoThemeColor.DIVIDER));

navBar.addNavigationItem(1, "首页", R.drawable.ic_home);
navBar.addNavigationItem(2, "分类", R.drawable.ic_category);

container.addView(navBar);
```

#### 5. 在 Layout XML 中使用

```xml
<com.yovo.yotheme.components.yo_navigation.YoNavigationBar
    android:id="@+id/nav_bar"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_alignParentBottom="true" />
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `addNavigationItem(int, String, int)` | 添加导航项 (ID, 标签, 图标资源ID) |
| `removeNavigationItem(int)` | 移除导航项 |
| `setSelectedItem(int)` | 设置选中项 |
| `setOnItemSelectedListener(OnItemSelectedListener)` | 设置项目选中监听 |
| `setOnItemReselectedListener(OnItemReselectedListener)` | 设置项目重复选中监听 |
| `setNavigationHeight(int)` | 设置导航栏高度 (dp) |
| `setMaxItemCount(int)` | 设置最大项目数 |
| `setDivider(boolean, int)` | 配置分割线 |

#### 样式参考

![image-20260122093603177](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093603177.png)

---

## YoScrollBar (滚动条)

### 简介
自定义滚动条组件，支持点击、悬浮、拖拽等交互，可与ScrollView联动。

### 包名
```
com.yovo.yotheme.components.yo_scrollbar.YoScrollBar
```

### 类名
- `YoScrollBar` - 主滚动条类

### 基本用法

#### 1. 创建滚动条并关联 ScrollView

```java
import com.yovo.yotheme.components.yo_scrollbar.YoScrollBar;

// 创建滚动条
YoScrollBar scrollBar = new YoScrollBar(this);

// 获取 ScrollView
ScrollView scrollView = findViewById(R.id.my_scroll_view);

// 将滚动条与 ScrollView 关联
scrollBar.attachToScrollView(scrollView);

// 添加到容器（通常与 ScrollView 重叠）
container.addView(scrollBar);
```

#### 2. 设置自动隐藏功能

```java
YoScrollBar scrollBar = new YoScrollBar(this);
ScrollView scrollView = findViewById(R.id.my_scroll_view);

// 启用自动隐藏（默认启用）
scrollBar.setAutoHideEnabled(true);

// 禁用自动隐藏
// scrollBar.setAutoHideEnabled(false);

scrollBar.attachToScrollView(scrollView);
container.addView(scrollBar);
```

#### 3. 手动控制滚动条显示/隐藏

```java
YoScrollBar scrollBar = new YoScrollBar(this);

// 显示滚动条
scrollBar.showScrollBar();

// 隐藏滚动条
scrollBar.hideScrollBar();

// 检查是否可见
boolean isVisible = scrollBar.isScrollBarVisible();
```

#### 4. 在 Layout XML 中使用

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <ScrollView
        android:id="@+id/my_scroll_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">
            <!-- 滚动内容 -->
        </LinearLayout>
        
    </ScrollView>
    
    <!-- 滚动条覆盖在 ScrollView 上 -->
    <com.yovo.yotheme.components.yo_scrollbar.YoScrollBar
        android:id="@+id/yo_scroll_bar"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
        
</FrameLayout>
```

### 主要方法

| 方法 | 描述 |
|------|------|
| `attachToScrollView(ScrollView)` | 将滚动条与 ScrollView 关联 |
| `setAutoHideEnabled(boolean)` | 启用/禁用自动隐藏 |
| `showScrollBar()` | 显示滚动条 |
| `hideScrollBar()` | 隐藏滚动条 |
| `isScrollBarVisible()` | 检查滚动条是否可见 |

#### 样式参考



---

## YoModalPanel (模态面板)

### 简介
核心模态面板组件，实现模态面板的主要功能。支持拖拽、动画、状态管理等功能。

### 包名
```
com.yovo.yotheme.components.yo_model_panel.YoModalPanel
```

### 类名
- `YoModalPanel` - 主模态面板类
- `YoModalPanelBuilder` - 面板构建器
- `YoModalPanelInterface` - 面板接口
- `YoModalPanelFactory` - 面板工厂

### 基本用法

#### 1. 创建简单模态面板

```java
import com.yovo.yotheme.components.yo_model_panel.YoModalPanel;

// 创建面板配置
YoModalPanelInterface.PanelConfig config = new YoModalPanelInterface.PanelConfig();

// 创建面板
YoModalPanel panel = new YoModalPanel(this, config);

// 创建内容视图
LinearLayout contentLayout = new LinearLayout(this);
contentLayout.setOrientation(LinearLayout.VERTICAL);

TextView titleView = new TextView(this);
titleView.setText("面板标题");
titleView.setTextSize(18);

Button closeButton = new Button(this);
closeButton.setText("关闭");
closeButton.setOnClickListener(v -> panel.dismiss());

contentLayout.addView(titleView);
contentLayout.addView(closeButton);

// 添加内容到面板
panel.addContentView(contentLayout);

// 显示面板
panel.show();
```

#### 2. 使用 Builder 创建面板

```java
YoModalPanelInterface.PanelConfig config = new YoModalPanelInterface.PanelConfig();
config.cancelable = true;
config.cancelableOnTouchOutside = true;
config.peekHeight = 300;

YoModalPanel panel = YoModalPanelBuilder.create(this, config)
    .setStateChangeListener(new YoModalPanelInterface.OnPanelStateChangeListener() {
        @Override
        public void onStateChanged(YoModalPanelInterface.PanelState state) {
            Log.d("YoModalPanel", "状态改变: " + state);
        }
    })
    .setInteractionListener(new YoModalPanelInterface.OnPanelInteractionListener() {
        @Override
        public void onPanelDragging(int offset) {
            Log.d("YoModalPanel", "拖拽偏移: " + offset);
        }
        
        @Override
        public void onPanelSwipeDismiss() {
            Log.d("YoModalPanel", "面板被滑动关闭");
        }
    })
    .build();

// 添加内容
LinearLayout content = new LinearLayout(this);
content.setLayoutParams(new LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    600
));
panel.addContentView(content);

panel.show();
```

#### 3. 配置面板参数

```java
YoModalPanelInterface.PanelConfig config = new YoModalPanelInterface.PanelConfig();

// 设置是否可以取消
config.cancelable = true;

// 设置点击外部是否可以取消
config.cancelableOnTouchOutside = true;

// 设置初始展示高度 (peek height)
config.peekHeight = 400;

// 设置动画时长
config.animationDuration = 300;

// 设置是否显示拖拽条
config.showDragBar = true;

YoModalPanel panel = new YoModalPanel(this, config);
panel.show();
```

#### 4. 设置面板事件监听

```java
YoModalPanel panel = new YoModalPanel(this);

// 设置状态变化监听
panel.setOnPanelStateChangeListener(new YoModalPanelInterface.OnPanelStateChangeListener() {
    @Override
    public void onStateChanged(YoModalPanelInterface.PanelState state) {
        switch(state) {
            case COLLAPSED:
                Log.d("YoModalPanel", "面板已折叠");
                break;
            case EXPANDED:
                Log.d("YoModalPanel", "面板已展开");
                break;
            case DRAGGING:
                Log.d("YoModalPanel", "面板正在拖拽");
                break;
        }
    }
});

// 设置交互监听
panel.setOnPanelInteractionListener(new YoModalPanelInterface.OnPanelInteractionListener() {
    @Override
    public void onPanelDragging(int offset) {
        // 处理拖拽事件
    }
    
    @Override
    public void onPanelSwipeDismiss() {
        // 处理滑动关闭事件
    }
});

// 设置生命周期监听
panel.setOnPanelLifecycleListener(new YoModalPanelInterface.OnPanelLifecycleListener() {
    @Override
    public void onPanelCreated() {
        Log.d("YoModalPanel", "面板已创建");
    }
    
    @Override
    public void onPanelShown() {
        Log.d("YoModalPanel", "面板已显示");
    }
    
    @Override
    public void onPanelHidden() {
        Log.d("YoModalPanel", "面板已隐藏");
    }
});

panel.show();
```

#### 5. 使用 Factory 创建标准面板

```java
// 使用工厂方法创建标准配置的面板
YoModalPanel panel = YoModalPanelFactory.createStandard(this, new YoModalPanelInterface.OnPanelStateChangeListener() {
    @Override
    public void onStateChanged(YoModalPanelInterface.PanelState state) {
        Log.d("YoModalPanel", "状态: " + state);
    }
});

// 添加内容并显示
LinearLayout content = createPanelContent();
panel.addContentView(content);
panel.show();
```

### PanelState 枚举

| 值 | 描述 |
|----|------|
| `COLLAPSED` | 面板已折叠 |
| `EXPANDED` | 面板已展开 |
| `DRAGGING` | 面板正在拖拽 |
| `HIDDEN` | 面板已隐藏 |

### 主要方法

| 方法 | 描述 |
|------|------|
| `addContentView(View)` | 添加内容视图 |
| `show()` | 显示面板 |
| `dismiss()` | 关闭面板 |
| `expand()` | 展开面板 |
| `collapse()` | 折叠面板 |
| `setOnPanelStateChangeListener(OnPanelStateChangeListener)` | 设置状态变化监听 |
| `setOnPanelInteractionListener(OnPanelInteractionListener)` | 设置交互监听 |
| `setOnPanelLifecycleListener(OnPanelLifecycleListener)` | 设置生命周期监听 |

#### 样式参考

![image-20260122093745887](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093745887.png)

---

## YoDialog (对话框)

### 简介
Yo主题对话框组件，提供风格一致的对话框样式。包括基础对话框、警告对话框、列表对话框和选择对话框等多种类型。提供白色背景、圆角矩形、标题、内容和按钮等基本功能，并支持自定义内容视图。

### 包名
```
com.yovo.yotheme.dialog.YoDialog
com.yovo.yotheme.dialog.YoAlertDialog
com.yovo.yotheme.dialog.YoListDialog
com.yovo.yotheme.dialog.YoSelectionDialog
com.yovo.yotheme.dialog.YoSelectConfirmDialog
```

### 类名
- `YoDialog` - 基础对话框类
- `YoAlertDialog` - 警告对话框类
- `YoListDialog` - 列表对话框类
- `YoSelectionDialog` - 单选对话框类
- `YoSelectConfirmDialog` - 确认选择对话框类

### 基本用法

#### 1. 创建基础对话框

```java
import com.yovo.yotheme.dialog.YoDialog;

YoDialog dialog = YoDialog.create(this)
    .setTitle("提示")
    .setMessage("这是一个对话框")
    .setPositiveButton("确定", (d, which) -> {
        // 处理确定按钮点击
        Toast.makeText(this, "点击了确定", Toast.LENGTH_SHORT).show();
    })
    .setNegativeButton("取消", (d, which) -> {
        // 处理取消按钮点击
    });

dialog.show();
```

#### 2. 创建警告对话框

```java
import com.yovo.yotheme.dialog.YoAlertDialog;

YoAlertDialog alertDialog = YoAlertDialog.create(this, "警告", "确定要执行此操作吗？")
    .setPositiveButton("确认", (d, which) -> {
        // 执行操作
    })
    .setNegativeButton("取消", null);

alertDialog.show();
```

#### 3. 创建列表对话框

```java
import com.yovo.yotheme.dialog.YoListDialog;

List<String> options = Arrays.asList("选项1", "选项2", "选项3");
YoListDialog listDialog = YoListDialog.create(this, "请选择", options);
listDialog.setOnOptionSelectedListener((index, option) -> {
    Toast.makeText(this, "选择了: " + option, Toast.LENGTH_SHORT).show();
});

listDialog.show();
```

#### 4. 创建单选对话框

```java
import com.yovo.yotheme.dialog.YoSelectionDialog;

List<String> options = Arrays.asList("选项A", "选项B", "选项C");
YoSelectionDialog selectionDialog = YoSelectionDialog.create(this, "单选", options)
    .setPositiveButton("确定", (d, which) -> {
        // 获取已选择的项
        int selectedIndex = selectionDialog.getSelectedIndex();
        Toast.makeText(this, "选择了索引: " + selectedIndex, Toast.LENGTH_SHORT).show();
    })
    .setNegativeButton("取消", null);

selectionDialog.show();
```

#### 5. 创建自定义内容对话框

```java
// 创建自定义内容视图
LinearLayout customContent = new LinearLayout(this);
customContent.setOrientation(LinearLayout.VERTICAL);

EditText editText = new EditText(this);
editText.setHint("输入内容");
customContent.addView(editText);

YoDialog customDialog = YoDialog.create(this)
    .setTitle("输入信息")
    .setCustomContentView(customContent)
    .setPositiveButton("提交", (d, which) -> {
        String input = editText.getText().toString();
        Toast.makeText(this, "输入: " + input, Toast.LENGTH_SHORT).show();
    })
    .setNegativeButton("取消", null);

customDialog.show();
```

#### 6. 配置对话框行为

```java
YoDialog dialog = YoDialog.create(this)
    .setTitle("可取消的对话框")
    .setMessage("点击外部区域或按返回键可关闭")
    .setPositiveButton("确定", null)
    .setNegativeButton("取消", null);

// 允许通过返回键取消
dialog.setCancelable(true);

// 允许通过点击外部区域取消
dialog.setCanceledOnTouchOutside(true);

// 设置取消监听
dialog.setOnCancelListener(d -> {
    Toast.makeText(this, "对话框已取消", Toast.LENGTH_SHORT).show();
});

dialog.show();
```

#### 7. 设置按钮颜色

```java
YoDialog dialog = YoDialog.create(this)
    .setTitle("彩色按钮")
    .setMessage("按钮可以自定义颜色")
    .setPositiveButton("确定", null, Color.GREEN)
    .setNegativeButton("取消", null, Color.RED);

dialog.show();
```

#### 8. 单个按钮居中显示

```java
YoDialog dialog = YoDialog.create(this)
    .setTitle("只有确定按钮")
    .setMessage("按钮居中显示")
    .setPositiveButton("确定", null)
    .setSingleButtonCentered(true);

dialog.show();
```

### YoDialog 主要方法

| 方法 | 描述 |
| --- | --- |
| `create(Context)` | 创建对话框实例 |
| `create(Context, String, String)` | 创建带标题和消息的对话框 |
| `setTitle(String)` | 设置对话框标题 |
| `setMessage(String)` | 设置对话框消息内容 |
| `setPositiveButton(String, OnClickListener)` | 设置确认按钮 |
| `setPositiveButton(String, OnClickListener, int)` | 设置确认按钮及其颜色 |
| `setNegativeButton(String, OnClickListener)` | 设置取消按钮 |
| `setNegativeButton(String, OnClickListener, int)` | 设置取消按钮及其颜色 |
| `setCustomContentView(View)` | 设置自定义内容视图 |
| `setSingleButtonCentered(boolean)` | 设置单个按钮是否居中显示 |
| `setIsEnableDismiss(boolean)` | 设置点击按钮后是否自动关闭对话框 |
| `show()` | 显示对话框 |
| `dismiss()` | 关闭对话框 |
| `setCancelable(boolean)` | 设置是否可通过返回键取消 |
| `setCanceledOnTouchOutside(boolean)` | 设置是否可通过点击外部取消 |
| `setOnCancelListener(OnCancelListener)` | 设置取消监听 |
| `setOnDismissListener(OnDismissListener)` | 设置关闭监听 |

#### 样式参考

![image-20260122093806223](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093806223.png)![image-20260122093830203](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093830203.png)![image-20260122093846833](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093846833.png)![image-20260122093858652](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093858652.png)![image-20260122093913170](C:\Users\498475\AppData\Roaming\Typora\typora-user-images\image-20260122093913170.png)

### YoAlertDialog

YoAlertDialog继承自YoDialog，主要特点是确认按钮使用错误/警告颜色（红色），用于提示用户执行危险操作。

**创建方法**：
```java
YoAlertDialog alertDialog = YoAlertDialog.create(this, "警告", "确定要删除吗？")
    .setPositiveButton("删除", (d, which) -> {
        // 执行删除操作
    })
    .setNegativeButton("取消", null);

alertDialog.show();
```

### YoListDialog

YoListDialog提供一个简单的项目列表，用户点击其中一项后对话框自动关闭。

**主要方法**：
```java
public interface OnOptionSelectedListener {
    void onOptionSelected(int index, String option);
}

YoListDialog listDialog = YoListDialog.create(this, "选择操作", Arrays.asList("编辑", "删除", "分享"));
listDialog.setOnOptionSelectedListener((index, option) -> {
    Toast.makeText(this, "选择: " + option, Toast.LENGTH_SHORT).show();
});
listDialog.show();
```

### YoSelectionDialog

YoSelectionDialog提供单选功能，用户可以选择一个选项，然后点击确认按钮。

**主要方法**：
```java
YoSelectionDialog selectionDialog = YoSelectionDialog.create(this, "选择一项", options);
selectionDialog.setPositiveButton("确定", (d, which) -> {
    int selectedIndex = selectionDialog.getSelectedIndex();
    String selectedOption = selectionDialog.getSelectedOption();
});
selectionDialog.show();
```

### YoSelectConfirmDialog

YoSelectConfirmDialog提供多选功能，用户可以选择多个选项。

**主要方法**：
```java
List<String> options = Arrays.asList("苹果", "香蕉", "橙子", "葡萄");
YoSelectConfirmDialog confirmDialog = YoSelectConfirmDialog.create(this, "选择水果", options);
confirmDialog.setPositiveButton("确定", (d, which) -> {
    List<Integer> selectedIndices = confirmDialog.getSelectedIndices();
    List<String> selectedOptions = confirmDialog.getSelectedOptions();
});
confirmDialog.show();
```

---
