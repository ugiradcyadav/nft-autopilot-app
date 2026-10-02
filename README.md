# NFT Autopilot V4 — Android Production App

Full Android NFT automation workstation: CREATE → MINT → LIST → MONITOR → SELL → VERIFY

**Stack:** Kotlin · Jetpack Compose · Room · web3j · Polygon PoS · Pinata IPFS · OpenSea API v2 · Hilt · Foreground Service

---

## Prerequisites

| Requirement | Detail |
|---|---|
| Android Studio | Hedgehog 2023.1+ |
| JDK | 17 |
| Android SDK | API 34 |
| Node.js | 18+ (for contract deployment only) |
| Polygon testnet MATIC | From [faucet.polygon.technology](https://faucet.polygon.technology) |

---

## Quick Start

### 1. Clone & Open
```bash
git clone <repo>
cd nft-autopilot-v4
# Open in Android Studio
```

### 2. Create `local.properties` (never commit this)
```properties
sdk.dir=/path/to/Android/Sdk
```

### 3. Deploy the Smart Contract (testnet first)
```bash
cd contracts
npm install
cp .env.example .env
# Fill DEPLOYER_PRIVATE_KEY, AMOY_RPC_URL, POLYGONSCAN_API_KEY in .env
npx hardhat run deploy.js --network amoy
# Save the contract address output
npx hardhat verify --network amoy <CONTRACT_ADDRESS> "Name" "SYMBOL" 10000 500 "<royalty_address>"
# Grant minter role to automation wallet:
CONTRACT_ADDRESS=0x... MINTER_ADDRESS=0x... npx hardhat run grant-minter.js --network amoy
```

### 4. Configure the App
Open the app → Settings and enter:
- **Pinata JWT** — from [pinata.cloud](https://pinata.cloud)
- **OpenSea API Key** — from [docs.opensea.io](https://docs.opensea.io)
- **RPC URL** — default: `https://rpc-amoy.polygon.technology`

### 5. Create a Wallet
App → Wallet → tap **+** → create HOT wallet → authenticate → note address → fund with testnet MATIC

### 6. Prepare Artwork Layers
Place PNG layers in:
```
app/src/main/assets/layers/<collection_id>/<layer_name>/<trait_value>.png
```
Example:
```
assets/layers/cyber-bharat/Background/blue.png
assets/layers/cyber-bharat/Body/humanoid.png
assets/layers/cyber-bharat/Eyes/laser.png
```

### 7. Run (testnet)
Build → Install APK → App opens → Create Collection → START automation

---

## Architecture Summary

```
ANDROID APP
  Foreground Service  ←→  Automation Engine  ←→  Job Queue (Room)
        ↓                        ↓
  WalletVault               PolygonRpc
  (Keystore AES)            (web3j secp256k1)
        ↓                        ↓
  Biometric/PIN          Polygon Amoy/Mainnet
                                 ↓
  IPFS (Pinata)  ←→     ERC-721 Contract
                                 ↓
  OpenSea API v2  ←→    Listings / Sales
```

## Wallet Security Model

```
Android Keystore (TEE)
    └─ AES-256-GCM key  [never leaves hardware]
           ↓ decrypts
    Encrypted private key blob  [stored in Room]
           ↓ in-memory only during signing
    web3j secp256k1 signing
           ↓
    Signed Polygon TX
           ↓ zeroed immediately after sign
    Private key wiped from memory
```

- Biometric/PIN required to start a 30-minute session
- Full-auto minting operates within the active session
- Session expires → automation pauses → re-auth required

---

## Release Gates (before mainnet)

| Gate | Description | Required |
|---|---|---|
| G0 | Build + no hardcoded secrets | ✅ |
| G1 | Local functional tests pass | ✅ |
| G2 | Testnet contract deployed + verified | ✅ |
| G3 | Testnet end-to-end pipeline | ✅ |
| G4 | Failure injection (15 scenarios) | ✅ |
| G5 | Soak test 100–500 NFTs | ✅ |
| G6 | Security audit | ✅ |
| G7 | Mainnet pilot (1 NFT) | ✅ |

**Do not enable mainnet until all gates = PASS.**

---

## Known Limitations / Next Steps

| Item | Status | Notes |
|---|---|---|
| OpenSea Seaport EIP-712 order signing | Skeleton | Full Seaport 1.5 order construction needed |
| AI artwork module | Optional | Plug in Stable Diffusion API or on-device model |
| Telegram bot | Not included | Add okhttp Telegram Bot API calls |
| Analytics/repricing | Basic | Extend PricingEngine with floor data |
| Magic Eden / Solana | V5 | Separate Solana wallet + Metaplex stack |
| Security screens | Stub | Expand SecurityCenterScreen |
| Collections screen | Stub | Build CollectionListScreen + CreateCollectionFlow |

---

## Project Structure

```
app/src/main/java/com/sadhu/nftautopilot/
  NFTAutopilotApp.kt          ← BouncyCastle registration + notification channels
  MainActivity.kt             ← Nav host
  di/AppModule.kt             ← Hilt providers
  data/database/
    AppDatabase.kt
    entity/Entities.kt        ← All 15 Room entities
    dao/*.kt                  ← All DAOs
  engine/
    wallet/
      WalletVault.kt          ← AES-Keystore encryption + secp256k1 via web3j
      NonceManager.kt         ← Mutex-serialized EVM nonce management
      WalletSession.kt        ← 30-min biometric session model
    blockchain/
      PolygonRpcProvider.kt   ← web3j Polygon RPC + mint + deploy + monitor
      ERC721Bytecode.kt       ← ABI reference
    ipfs/
      IpfsStorageEngine.kt    ← Pinata upload + CID verification
    marketplace/
      OpenSeaAdapter.kt       ← OpenSea v2 API — listings, offers, sales
    artwork/
      ArtworkGenerator.kt     ← Android Canvas PNG layer compositor
      RarityEngine.kt         ← Score + rank assignment
      DuplicateDetector.kt    ← SHA-256 + pHash (DCT-based)
    automation/
      AutomationEngine.kt     ← Job dispatcher + all pipeline handlers
      NftStateMachine.kt      ← Valid state transitions + enforcement
  service/
    AutomationForegroundService.kt  ← START_STICKY foreground service
    BootReceiver.kt           ← Restart after reboot
  ui/
    theme/Theme.kt            ← Material 3 dark theme
    screen/
      DashboardScreen.kt      ← Main dashboard with health + stats
      WalletScreen.kt         ← Wallet management + biometric auth
      NftListScreen.kt        ← NFT list per collection
      LogsScreen.kt           ← Audit log viewer
      SettingsScreen.kt       ← API keys + RPC + mainnet toggle
  util/
    HashUtil.kt               ← SHA-256 file hashing
    AuditLogger.kt            ← Fire-and-forget audit logging
contracts/
  SadhuNFT.sol               ← OpenZeppelin ERC-721 + ERC-2981 royalties
  hardhat.config.js
  deploy.js
  grant-minter.js
```

---

## Package ID
`com.sadhu.nftautopilot`

