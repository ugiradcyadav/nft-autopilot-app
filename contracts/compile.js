const solc   = require("solc");
const path   = require("path");
const fs     = require("fs");

const contractSource = fs.readFileSync("SadhuNFT.sol", "utf8");

function findImports(importPath) {
  try {
    const fullPath = path.join(__dirname, "node_modules", importPath);
    return { contents: fs.readFileSync(fullPath, "utf8") };
  } catch (e) {
    return { error: "File not found: " + importPath };
  }
}

const input = {
  language: "Solidity",
  sources: { "SadhuNFT.sol": { content: contractSource } },
  settings: {
    optimizer: { enabled: true, runs: 200 },
    outputSelection: { "*": { "*": ["abi", "evm.bytecode.object"] } }
  }
};

const output = JSON.parse(
  solc.compile(JSON.stringify(input), { import: findImports })
);

if (output.errors) {
  const errs = output.errors.filter(e => e.severity === "error");
  if (errs.length) {
    console.error("COMPILE ERRORS:");
    errs.forEach(e => console.error(e.formattedMessage));
    process.exit(1);
  }
  // warnings only
  output.errors.forEach(e => console.warn("WARN:", e.message.split("\n")[0]));
}

const contract = output.contracts["SadhuNFT.sol"]["SadhuNFT"];
const bytecode = "0x" + contract.evm.bytecode.object;
const abi      = contract.abi;

console.log("BYTECODE_LENGTH=" + bytecode.length);
console.log("ABI_FUNCTIONS=" + abi.filter(x => x.type === "function").map(x => x.name).join(","));

fs.writeFileSync("compiled.json", JSON.stringify({ bytecode, abi }, null, 2));
console.log("Written: compiled.json");
