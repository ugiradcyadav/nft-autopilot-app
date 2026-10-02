// Run after deployment to grant MINTER_ROLE to the automation wallet
const { ethers } = require("hardhat");

async function main() {
  const contractAddress = process.env.CONTRACT_ADDRESS;
  const minterAddress   = process.env.MINTER_ADDRESS;

  if (!contractAddress || !minterAddress) {
    throw new Error("Set CONTRACT_ADDRESS and MINTER_ADDRESS env vars");
  }

  const [owner] = await ethers.getSigners();
  const contract = await ethers.getContractAt("SadhuNFT", contractAddress, owner);

  const MINTER_ROLE = await contract.MINTER_ROLE();
  const tx = await contract.grantRole(MINTER_ROLE, minterAddress);
  await tx.wait();

  const hasRole = await contract.hasRole(MINTER_ROLE, minterAddress);
  console.log(`✅ MINTER_ROLE granted to ${minterAddress}: ${hasRole}`);
}

main().catch(e => { console.error(e); process.exit(1); });
