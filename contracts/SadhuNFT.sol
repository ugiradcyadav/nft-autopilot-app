// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721Pausable.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/token/common/ERC2981.sol";

/**
 * @title SadhuNFT
 * @notice Production ERC-721 for NFT Autopilot V4 — Polygon PoS
 *
 * Role separation:
 *   DEFAULT_ADMIN_ROLE  → owner/admin wallet  (cold, user-controlled)
 *   MINTER_ROLE         → automation wallet   (hot, app-controlled)
 *
 * Deploy flow:
 *   npx hardhat run contracts/deploy.js --network amoy
 *   npx hardhat run contracts/grant-minter.js --network amoy
 *   Enter contract address in app Settings.
 */
contract SadhuNFT is
    ERC721,
    ERC721URIStorage,
    ERC721Pausable,
    AccessControl,
    ERC2981
{
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");

    uint256 private _nextTokenId;
    uint256 public immutable maxSupply;
    bool    public metadataFrozen;

    event MetadataFrozen();
    event BatchMinted(address indexed to, uint256 fromId, uint256 toId);

    error MaxSupplyReached();
    error MetadataAlreadyFrozen();
    error EmptyBatch();

    constructor(
        string memory name_,
        string memory symbol_,
        uint256 maxSupply_,
        uint96  royaltyBps_,
        address royaltyReceiver_
    ) ERC721(name_, symbol_) {
        require(maxSupply_       >    0, "Max supply = 0");
        require(royaltyBps_      <= 1000, "Royalty > 10%");
        require(royaltyReceiver_ != address(0), "Bad royalty addr");

        maxSupply = maxSupply_;
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(MINTER_ROLE,        msg.sender);
        _setDefaultRoyalty(royaltyReceiver_, royaltyBps_);
    }

    // ─── Mint ──────────────────────────────────────────────────────────────────

    function safeMint(
        address to,
        string calldata uri
    ) external onlyRole(MINTER_ROLE) whenNotPaused returns (uint256 tokenId) {
        if (_nextTokenId >= maxSupply) revert MaxSupplyReached();
        if (metadataFrozen)           revert MetadataAlreadyFrozen();
        tokenId = _nextTokenId++;
        _safeMint(to, tokenId);
        _setTokenURI(tokenId, uri);
    }

    function batchMint(
        address to,
        string[] calldata uris
    ) external onlyRole(MINTER_ROLE) whenNotPaused {
        uint256 count = uris.length;
        if (count == 0) revert EmptyBatch();
        if (_nextTokenId + count > maxSupply) revert MaxSupplyReached();
        if (metadataFrozen) revert MetadataAlreadyFrozen();

        uint256 fromId = _nextTokenId;
        for (uint256 i; i < count; ++i) {
            uint256 id = _nextTokenId++;
            _safeMint(to, id);
            _setTokenURI(id, uris[i]);
        }
        emit BatchMinted(to, fromId, _nextTokenId - 1);
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    function pause()   external onlyRole(DEFAULT_ADMIN_ROLE) { _pause(); }
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) { _unpause(); }

    function freezeMetadata() external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (metadataFrozen) revert MetadataAlreadyFrozen();
        metadataFrozen = true;
        emit MetadataFrozen();
    }

    function setDefaultRoyalty(
        address receiver,
        uint96  feeNumerator
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _setDefaultRoyalty(receiver, feeNumerator);
    }

    // ─── Views ────────────────────────────────────────────────────────────────

    function totalMinted() external view returns (uint256) { return _nextTokenId; }

    // ─── Required overrides ───────────────────────────────────────────────────

    function _update(address to, uint256 tokenId, address auth)
        internal override(ERC721, ERC721Pausable) returns (address)
    { return super._update(to, tokenId, auth); }

    function tokenURI(uint256 tokenId)
        public view override(ERC721, ERC721URIStorage) returns (string memory)
    { return super.tokenURI(tokenId); }

    function supportsInterface(bytes4 interfaceId)
        public view override(ERC721, ERC721URIStorage, AccessControl, ERC2981) returns (bool)
    { return super.supportsInterface(interfaceId); }
}
