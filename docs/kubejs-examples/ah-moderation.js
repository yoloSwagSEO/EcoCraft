// EcoCraft Auction House — KubeJS Example Scripts
// Place in: kubejs/server_scripts/

// Block listings of specific items
EcocraftAHEvents.listingCreating(event => {
  if (event.itemId.includes('netherite')) {
    event.setMessage('Netherite items cannot be sold on the AH!')
    event.cancel()
  }
})

// Max price enforcement
EcocraftAHEvents.listingCreating(event => {
  if (event.price > 100000) {
    event.setMessage('Maximum listing price is 100,000!')
    event.cancel()
  }
})

// Announce big sales server-wide
EcocraftAHEvents.sold(event => {
  if (event.totalPrice > 5000) {
    event.buyer.server.tell(`[HDV] ${event.buyer.name.string} bought ${event.itemName} for ${event.totalPrice} Gold!`)
  }
})

// Log all bids
EcocraftAHEvents.bidPlaced(event => {
  console.log(`[HDV] Bid: ${event.bidder.name.string} placed ${event.amount} on listing ${event.listingId}`)
})

// Notify when auctions expire
EcocraftAHEvents.listingExpired(event => {
  console.log(`[HDV] Listing expired: ${event.itemName} by ${event.sellerName} (had bids: ${event.hadBids()})`)
})

// Read data with bindings
// let bestPrice = AHAuctions.getBestPrice('minecraft:diamond')
// let stats = AHAuctions.getPlayerStats(player)
// console.log(`Player sold: ${stats.totalSales}, spent: ${stats.totalSpent}`)
