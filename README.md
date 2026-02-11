# HAMGIS-drop

## 概述
HAMGIS-drop 是一个 Android 应用程序，用于接收来自 HAMGIS 手表端的地理测量数据，并支持多种格式导出。该应用提供了 HTTP 服务器和 BLE 服务器两种数据接收方式，方便用户在不同场景下使用。

## 功能特性
- **多协议支持**：同时支持 HTTP 和 BLE 两种数据接收方式
- **多种导出格式**：支持将数据导出为 CSV、JSON、GeoJSON 和 KML 格式
- **国际化支持**：支持中文、英文和日文三种语言
- **详细的使用说明**：内置帮助页面，提供完整的使用文档
- **实时数据预览**：接收数据后可直接预览
- **Material Design 3 UI**：现代化的用户界面设计

## 安装方法
1. 确保您的设备运行 Android 8.0（API 级别 26）或更高版本
2. 克隆或下载本项目
3. 使用 Android Studio 打开项目
4. 连接您的 Android 设备或启动模拟器
5. 点击 "运行" 按钮安装应用

## 使用说明

### 1. 启动应用
- 安装完成后，在设备上找到 "HAMGIS レシーバー" 应用并打开

### 2. 接收数据

#### HTTP 方式
- 点击 "サーバー起動" 按钮启动 HTTP 服务器
- 确保手表和手机连接到同一局域网
- 在手表端选择 "导出到 HTTP" 选项
- 手表会自动搜索并连接到手机的 HTTP 服务器
- 数据传输完成后，应用会显示 "数据已接收！"

#### BLE 方式
- 确保手机蓝牙已开启
- 在手表端选择 "导出到 BLE" 选项
- 手机会自动搜索并连接到手表
- 数据传输完成后，应用会显示 "数据已接收！"

### 3. 导出数据
- 数据接收完成后，您可以选择以下格式导出：
  - **CSV**：适合在 Excel 中查看，包含项目摘要和点位详情
  - **GeoJSON**：标准 GIS 格式，支持 ArcGIS、QGIS 等软件
  - **KML**：Google Earth 格式，包含坐标和属性信息
  - **JSON**：完整的项目数据，格式化显示

### 4. 查看使用说明
- 点击 "使用说明" 按钮查看详细的使用文档
- 包含：概述、工作流程、手表端操作、手机端操作、导出功能、工作模式、数据同步和故障排除

## 支持的导出格式

| 格式 | 用途 | 特点 |
|------|------|------|
| CSV | 数据表格 | 适合 Excel 查看，包含摘要和点位详情 |
| GeoJSON | GIS 分析 | 标准 GIS 格式，支持主流 GIS 软件 |
| KML | Google Earth | 可视化展示，适合直观查看 |
| JSON | 数据交换 | 完整项目数据，结构化存储 |

## 国际化支持

应用支持以下语言：
- 中文（简体）
- 英文
- 日文

语言会根据设备系统语言自动切换。

## 项目结构

```
app/
├── src/
│   └── main/
│       ├── java/top/hsyscn/hamgis/
│       │   ├── BleServerManager.kt     # BLE 服务器管理
│       │   ├── HelpActivity.kt         # 帮助页面
│       │   ├── HttpServerManager.kt    # HTTP 服务器管理
│       │   ├── MainActivity.kt         # 主界面
│       │   └── ui/theme/               # UI 主题
│       ├── res/                        # 资源文件
│       │   ├── values/                 # 英文资源
│       │   ├── values-zh/              # 中文资源
│       │   └── values-ja/              # 日文资源
│       └── AndroidManifest.xml         # 应用配置
└── build.gradle.kts                    # 构建配置
```

## 技术栈

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **目标 SDK**：Android 34
- **最低 SDK**：Android 26
- **HTTP 服务器**：基于 NanoHTTPD
- **BLE 支持**：Android BLE API
- **JSON 处理**：Android 内置 JSON 库

## 许可证

本项目采用 MIT 许可证。详情请查看 LICENSE 文件。

## 联系方式

如果您有任何问题或建议，欢迎通过以下方式联系我们：

- 邮件：bugreport@hsyscn.top

---

# HAMGIS-drop

## Overview
HAMGIS-drop is an Android application for receiving geodetic measurement data from HAMGIS watch devices and supporting multiple export formats. The app provides both HTTP server and BLE server functionality for data reception, making it convenient for users in different scenarios.

## Features
- **Multi-protocol support**: Supports both HTTP and BLE data reception methods
- **Multiple export formats**: Export data to CSV, JSON, GeoJSON, and KML formats
- **Internationalization**: Supports Chinese, English, and Japanese languages
- **Detailed user guide**: Built-in help page with complete documentation
- **Real-time data preview**: Preview data immediately after reception
- **Material Design 3 UI**: Modern user interface design

## Installation
1. Ensure your device is running Android 8.0 (API level 26) or higher
2. Clone or download this project
3. Open the project with Android Studio
4. Connect your Android device or start an emulator
5. Click the "Run" button to install the app

## Usage

### 1. Launch the App
- After installation, find the "HAMGIS Receiver" app on your device and open it

### 2. Receive Data

#### HTTP Method
- Click the "Start Server" button to launch the HTTP server
- Ensure the watch and phone are connected to the same LAN
- Select "Export to HTTP" on the watch
- The watch will automatically search for and connect to the phone's HTTP server
- After successful data transfer, the app will display "Data received!"

#### BLE Method
- Ensure Bluetooth is enabled on your phone
- Select "Export to BLE" on the watch
- The phone will automatically search for and connect to the watch
- After successful data transfer, the app will display "Data received!"

### 3. Export Data
- After data reception, you can choose from the following export formats:
  - **CSV**: Suitable for viewing in Excel, contains project summary and point details
  - **GeoJSON**: Standard GIS format, supported by ArcGIS, QGIS, etc.
  - **KML**: Google Earth format, includes coordinates and attribute information
  - **JSON**: Complete project data with formatted display

### 4. View User Guide
- Click the "User Guide" button to view detailed documentation
- Includes: Overview, Workflow, Watch Operation, Phone Operation, Export Functions, Working Modes, Data Sync, and Troubleshooting

## Supported Export Formats

| Format | Purpose | Features |
|--------|---------|----------|
| CSV | Data tables | Suitable for Excel viewing, contains summary and point details |
| GeoJSON | GIS analysis | Standard GIS format, supported by mainstream GIS software |
| KML | Google Earth | Visual display, suitable for intuitive viewing |
| JSON | Data exchange | Complete project data, structured storage |

## Internationalization

The app supports the following languages:
- Chinese (Simplified)
- English
- Japanese

The language automatically switches according to the device system language.

## Project Structure

```
app/
├── src/
│   └── main/
│       ├── java/top/hsyscn/hamgis/
│       │   ├── BleServerManager.kt     # BLE server management
│       │   ├── HelpActivity.kt         # Help page
│       │   ├── HttpServerManager.kt    # HTTP server management
│       │   ├── MainActivity.kt         # Main interface
│       │   └── ui/theme/               # UI themes
│       ├── res/                        # Resource files
│       │   ├── values/                 # English resources
│       │   ├── values-zh/              # Chinese resources
│       │   └── values-ja/              # Japanese resources
│       └── AndroidManifest.xml         # App configuration
└── build.gradle.kts                    # Build configuration
```

## Technology Stack

- **Development Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Target SDK**: Android 34
- **Minimum SDK**: Android 26
- **HTTP Server**: Based on NanoHTTPD
- **BLE Support**: Android BLE API
- **JSON Processing**: Android built-in JSON library

## License

This project is licensed under the MIT License. See the LICENSE file for details.

## Contact

If you have any questions or suggestions, please feel free to contact us through:

- Email: bugreport@hsyscn.top

---

# HAMGIS-drop

## 概要
HAMGIS-drop は、HAMGIS ウォッチ端末から測地データを受信し、複数の形式でエクスポートするための Android アプリケーションです。このアプリは、HTTP サーバーと BLE サーバーの両方のデータ受信機能を提供し、ユーザーが異なるシナリオで使用できるようにしています。

## 機能
- **マルチプロトコルサポート**：HTTP と BLE の両方のデータ受信方式をサポート
- **複数のエクスポート形式**：CSV、JSON、GeoJSON、KML 形式でデータをエクスポート
- **国際化サポート**：中国語、英語、日本語の3つの言語をサポート
- **詳細なユーザーガイド**：完全なドキュメントを備えた組み込みヘルプページ
- **リアルタイムデータプレビュー**：受信後すぐにデータをプレビュー
- **Material Design 3 UI**：最新のユーザーインターフェイス設計

## インストール方法
1. デバイスが Android 8.0（API レベル 26）以上であることを確認
2. このプロジェクトをクローンまたはダウンロード
3. Android Studio でプロジェクトを開く
4. Android デバイスを接続するかエミュレータを起動
5. 「実行」ボタンをクリックしてアプリをインストール

## 使用方法

### 1. アプリを起動
- インストールが完了したら、デバイスで「HAMGIS レシーバー」アプリを見つけて開く

### 2. データを受信

#### HTTP 方式
- 「サーバー起動」ボタンをクリックして HTTP サーバーを起動
- ウォッチとスマホが同じ LAN に接続されていることを確認
- ウォッチ側で「HTTP にエクスポート」オプションを選択
- ウォッチは自動的にスマホの HTTP サーバーを検索して接続
- データ転送が完了すると、アプリに「データを受信しました！」と表示されます

#### BLE 方式
- スマホの Bluetooth がオンになっていることを確認
- ウォッチ側で「BLE にエクスポート」オプションを選択
- スマホは自動的にウォッチを検索して接続
- データ転送が完了すると、アプリに「データを受信しました！」と表示されます

### 3. データをエクスポート
- データ受信が完了したら、以下の形式から選択してエクスポートできます：
  - **CSV**：Excel で表示するのに適し、プロジェクトの概要とポイントの詳細を含む
  - **GeoJSON**：標準 GIS 形式、ArcGIS、QGIS などでサポート
  - **KML**：Google Earth 形式、座標と属性情報を含む
  - **JSON**：完全なプロジェクトデータ、フォーマット表示

### 4. 使用説明を表示
- 「ユーザーガイド」ボタンをクリックして詳細な使用説明を表示
- 内容：概要、ワークフロー、ウォッチ操作、スマホ操作、エクスポート機能、動作モード、データ同期、トラブルシューティング

## サポートされているエクスポート形式

| 形式 | 用途 | 特徴 |
|------|------|------|
| CSV | データテーブル | Excel での表示に適し、概要とポイントの詳細を含む |
| GeoJSON | GIS 分析 | 標準 GIS 形式、主流の GIS ソフトウェアでサポート |
| KML | Google Earth | 視覚的な表示、直感的な確認に適し |
| JSON | データ交換 | 完全なプロジェクトデータ、構造化ストレージ |

## 国際化サポート

このアプリは次の言語をサポートしています：
- 中国語（簡体字）
- 英語
- 日本語

言語はデバイスのシステム言語に応じて自動的に切り替わります。

## プロジェクト構造

```
app/
├── src/
│   └── main/
│       ├── java/top/hsyscn/hamgis/
│       │   ├── BleServerManager.kt     # BLE サーバー管理
│       │   ├── HelpActivity.kt         # ヘルプページ
│       │   ├── HttpServerManager.kt    # HTTP サーバー管理
│       │   ├── MainActivity.kt         # メインインターフェース
│       │   └── ui/theme/               # UI テーマ
│       ├── res/                        # リソースファイル
│       │   ├── values/                 # 英語リソース
│       │   ├── values-zh/              # 中国語リソース
│       │   └── values-ja/              # 日本語リソース
│       └── AndroidManifest.xml         # アプリ設定
└── build.gradle.kts                    # ビルド設定
```

## 技術スタック

- **開発言語**：Kotlin
- **UI フレームワーク**：Jetpack Compose
- **ターゲット SDK**：Android 34
- **最小 SDK**：Android 26
- **HTTP サーバー**：NanoHTTPD ベース
- **BLE サポート**：Android BLE API
- **JSON 処理**：Android 組み込み JSON ライブラリ

## ライセンス

このプロジェクトは MIT ライセンスに基づいています。詳細については LICENSE ファイルを参照してください。

## お問い合わせ

質問や提案がある場合は、以下の方法でお問い合わせください：

- メール：bugreport@hsyscn.top