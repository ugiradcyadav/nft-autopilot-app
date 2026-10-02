const { ethers } = require("hardhat");

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying with:", deployer.address);
  console.log("Balance:", ethers.formatEther(await deployer.provider.getBalance(deployer.address)), "MATIC");

  const name           = process.env.COLLECTION_NAME    || "Cyber Bharat 2047";
  const symbol         = process.env.COLLECTION_SYMBOL  || "CB47";
  const maxSupply      = parseInt(process.env.MAX_SUPPLY || "10000");
  const royaltyBps     = parseInt(process.env.ROYALTY_BPS || "500");
  const royaltyAddress = process.env.ROYALTY_ADDRESS    || deployer.address;

  console.log(`\nDeploying: ${name} (${symbol}) — supply: ${maxSupply} royalty: ${royaltyBps}bps`);

  const SadhuNFT = await ethers.getContractFactory("SadhuNFT");
  const contract = await SadhuNFT.deploy(name, symbol, maxSupply, royaltyBps, royaltyAddress);
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  console.log("\n✅ Contract deployed:", address);
  console.log("TX:", contract.deploymentTransaction()?.hash);
  console.log("\nNext steps:");
  console.log("1. Verify: npx hardhat verify --network amoy", address, `"${name}" "${symbol}" ${maxSupply} ${royaltyBps} "${royaltyAddress}"`);
  console.log("2. Grant MINTER_ROLE to automation wallet");
  console.log("3. Enter contract address in app Settings");
}

main().catch(e => { console.error(e); process.exit(1); });
