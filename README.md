# 属性测试框架

Pair-wise GSB 标注任务仓库（第 16 批 / 247）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们想用随机数据自动发现边界缺陷，失败时要能自动缩小到最小反例。请从零实现一个属性测试框架，**不允许依赖 jqwik、QuickTheories 等现成实现**。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持定义属性：给定随机输入生成器与断言函数，重复执行若干次；2) 支持基础生成器：整数与字符串（含边界值）、集合、以及组合生成器，并能配置取值范围；3) 支持随机种子：指定种子后生成序列完全可复现；4) 支持收缩（shrinking）：属性失败后自动把输入缩小到更小的反例，需说明收缩算法与保证；5) 支持分类统计：把输入划分到不同类别并统计各类别执行次数，便于判断覆盖是否偏斜；6) 支持失败收集：一次运行中发现多个反例时全部保留并去重；7) 提供统计：执行次数、失败次数、收缩步数与各类别分布；8) 测试覆盖属性通过、属性失败并收缩到最小反例、种子可复现、分类统计与失败收集；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

---

# 实现说明：属性测试框架（`com.example.pbt`）

纯 JDK 实现，无 jqwik / QuickTheories 等第三方属性测试依赖（仅测试作用域的 JUnit 5、AssertJ）。

## 快速开始

```java
PropertyCheck.forAll(Arbitraries.integers().between(0, 100).build())
    .check(i -> i < 50)                 // 谓词返回 false 即反例
    .withTries(200)                     // 重复执行次数
    .withSeed(42L)                      // 固定种子，完全可复现
    .classify("small", i -> i < 10)     // 分类统计
    .run()
    .assertSuccessful();                // 失败时打印收缩后的反例
```

也支持断言风格（AssertJ 抛出的 `AssertionError` 视为反例）：

```java
PropertyCheck.forAll(Arbitraries.strings().between('a', 'z').build())
    .checkAssert(s -> assertThat(s.length()).isLessThan(3));
```

## 生成器

| 生成器 | 范围配置 | 边界值注入 |
|--------|----------|-----------|
| `integers().between(min,max)` | 闭区间 | 端点、0、±1、端点±1（约 15% 概率） |
| `strings().between(c1,c2).ofMinLength/ofMaxLength/ofLength` | 字符区间与长度区间 | 空串、最短/最长串、边界字符 |
| `lists(arbitrary).between(min,max).ofMinSize/ofMaxSize/ofSize` | 元素生成器 + 尺寸区间 | 空列表、最小/最大尺寸 |
| `combine(a,b).as(...)` / `combine3(...)` | 组合生成器 | 各分量可独立收缩 |
| `constant(v)` / `oneOf(...)` | 常量 / 等概率选择 | — |
| `arbitrary.map/fmap/filter` | 派生生成器 | filter 连续丢弃 1 万次快速失败 |

## 随机种子

- `RandomSource` 使用自实现的 SplitMix64，不依赖 JDK 的 `Random` 实现细节；
- `withSeed(long)` 后，输入序列、失败样本、收缩过程、分类分布逐位可复现；
- 未指定种子时使用随机种子，结果中会报告实际种子（`statistics().seed()`），便于事后重放。

## 收缩算法与保证

生成器产出的是惰性“收缩树” `Shrinkable<T>`：每个值携带一串严格更小的候选项。

- **整数**：朝零收缩，步长序列 1, 2, 4, 8, …（截断在区间内），候选最小者优先；
- **字符串**：先缩短长度（每次砍掉多余长度的一半，再砍到最短长度），再逐字符向最小字符简化（步长 1, 2, 4, …）；
- **列表**：先按“从大到小的连续块、每个位置”删除元素，再对每个元素原地收缩；
- **组合值**：先收缩第一分量，再第二、第三分量。

引擎（`Shrinker`）做**贪心下降**：反复在候选中取第一个仍然违反属性的子值，直到没有任何直接子值仍违反为止，并用 `withMaxShrinks` 限定步数。

保证：

1. **终止性**：每个生成器的子值在一个有界序上严格更小（离零更近、更短、元素更少），不可能成环；
2. **可靠性**：每一步都重新执行属性，返回值必然仍是反例；
3. **1-最小性**：结果不存在任何“一步可达”的更小反例；对单调整数谓词（如 `i < k`），因候选始终包含 `value±1`，1-最小即全局最小（测试中 `i < 50` 必收缩到 `50`，列表/字符串属性分别收缩到 `[0,0,0]` 和 `"aaa"`）；
4. 非全局最小也是可接受的语义：框架只承诺 1-最小，不承诺任意复杂谓词下的全局最小。

## 失败收集与统计

- 一次运行中所有失败都会继续跑完，反例先收缩再按值 `equals` **去重**，保留首次出现顺序；
- `PropertyResult.counterexamples()` 同时保留原始样本、收缩结果与各反例的收缩步数；
- `PropertyStatistics` 提供：`tries`（执行次数）、`failures`（失败次数）、`shrinkSteps`（收缩总步数）、
  `classification`（`classify(label, predicate)` 的标签计数，以及 `collect(label, fn)` 的派生桶计数）。

## 测试覆盖

`src/test/java/com/example/pbt` 下 5 个测试类：

- `PassingPropertyTest`：属性通过（列表反转、断言风格、组合/过滤生成器）；
- `ShrinkingTest`：整数/字符串/列表失败后收缩到最小反例，保留原始样本；
- `SeedReproducibilityTest`：同种子的随机序列、完整运行结果（含收缩）与分类分布一致；
- `ClassificationTest`：`classify` 穷举分类、`collect` 派生桶、非穷举分类计数；
- `FailureCollectionTest`：多个不同反例全部保留、重复反例去重、失败不中断运行。
