# Greek Package Tracker 

<div align="center">
  <a href='https://play.google.com/store/apps/details?id=com.spgrvl.packagetracker'>
    <img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height="80px"/>
  </a>
</div>

<div align="center">
  <strong>📦 Track all your Greek packages in one place! 🇬🇷</strong>
</div>

---

## About

Greek Package Tracker is an Android application designed to help users track their packages from multiple Greek carriers in a single, unified interface. No more switching between different carrier websites or apps - track everything from one convenient location and get notified for any updates!

## Features

- **Multi-Carrier Support**: Track packages from 16 Greek carriers, plus orders from several e-shops
- **Automatic Carrier Detection**: Just enter the tracking number, the app finds the carrier, or choose it yourself
- **Courier Hand-offs**: Follow a parcel across carriers in one timeline, e.g. ACS → Skroutz Last Mile or a Plaisio order → its courier
- **Barcode Scanning**: Quickly add packages by scanning tracking codes
- **Clipboard Detection**: Optionally offers to add a tracking number you copied
- **Auto-Refresh**: Automatic background updates for package status
- **Notifications**: Get notified when package status changes
- **Delivery Status at a Glance**: Pending, in transit, out for delivery, delivered or issue
- **Package History**: Keep track of completed deliveries, and search all your packages
- **Backup & Restore**: Export your packages and restore them on another device
- **Modern UI**: Clean, intuitive interface with Material Design
- **Dark Mode**: Full dark theme support
- **Multi-Language**: Support for Greek and English, with carrier updates translated where carriers only publish them in Greek

## Supported Carriers

| Carrier | Website |
|---------|---------|
| **ELTA / ELTA Courier** | [elta.gr](https://www.elta.gr/) |
| **ACS Courier** | [acscourier.net](https://www.acscourier.net/) |
| **Geniki Taxydromiki** | [taxydromiki.com](https://www.taxydromiki.com/) |
| **Speedex** | [speedex.gr](https://www.speedex.gr/) |
| **BoxNow** | [boxnow.gr](https://boxnow.gr/) |
| **Skroutz Last Mile** | [skroutzlastmile.gr](https://www.skroutzlastmile.gr/) |
| **Easy Mail** | [easymail.gr](https://www.easymail.gr/) |
| **Courier Center** | [courier.gr](https://www.courier.gr/) |
| **Clever Point** | [cleverpoint.gr](https://www.cleverpoint.gr/) |
| **Comet Hellas** | [comethellas.gr](https://www.comethellas.gr/) |
| **Delatolas Courier** | [delatolascourier.gr](https://www.delatolascourier.gr/) |
| **Diakinisis** | [diakinisis.gr](https://www.diakinisis.gr/) |
| **Aiolos Courier** | [aioloscourier.gr](https://aioloscourier.gr/) |
| **Biz Courier** | [bizcourier.eu](https://www.bizcourier.eu/) |
| **Pack-Man Courier** | [pack-man.gr](https://pack-man.gr/) |

### Supported Stores

Orders are tracked with their order number on the store's own page. Plaisio orders then continue with the courier they are handed to, in the same timeline.

| Store | Website |
|-------|---------|
| **Plaisio** | [plaisio.gr](https://www.plaisio.gr/) |
| **IKEA** | [ikea.gr](https://www.ikea.gr/) |
| **e-shop.gr** | [e-shop.gr](https://www.e-shop.gr/) |

## Technical Details

- **Platform**: Android (API 24+)
- **Language**: Java
- **Architecture**: Native Android with SQLite database
- **Libraries**: Jsoup, OkHttp, WorkManager, Google Mobile Vision (barcode scanning)
- **Minimum Android Version**: 7.0 (API 24)
- **Target SDK**: 37 (Android 17)

## Getting Started

### Installation

1. **From Google Play Store** (Recommended):
   - Download from the [Google Play Store](https://play.google.com/store/apps/details?id=com.spgrvl.packagetracker)

2. **Build from Source**:
   ```bash
   git clone https://github.com/spgrvl/package-tracker-app.git
   cd package-tracker-app
   ./gradlew assembleDebug
   ```
   Run the unit tests with `./gradlew testDebugUnitTest`.

### Usage

1. **Add a Package**: 
   - Tap **New package** in the bottom bar and enter a tracking number, optionally with a name and carrier
   - Or tap **Scan** in the same dialog to scan a barcode
   
2. **Track Progress**: 
   - See every package's latest status on the **Active** tab
   - Pull down to refresh manually
   - Tap any package for its full tracking timeline; tap links in updates to open the courier's own page
   
3. **Manage Packages**: 
   - Mark packages as completed from their details screen; find them later on the **Completed** tab
   - Search packages by name or tracking number
   - Long press to bulk select packages
   - Edit, delete, copy details or open the carrier's website from the details screen's menu

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

### Development Setup

1. Clone the repository
2. Open in Android Studio
3. Sync project with Gradle files
4. Run on device or emulator

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.