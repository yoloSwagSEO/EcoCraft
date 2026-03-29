// EcoCraft Economy — KubeJS Example Scripts
// Place in: kubejs/server_scripts/

// Log all transactions
EcocraftEvents.transactionAfter(event => {
  console.log(`[Eco] ${event.player.name.string}: ${event.type} ${event.amount} ${event.currency} (${event.success ? 'OK' : 'FAILED'})`)
})

// Block withdrawals over 10000
EcocraftEvents.transaction(event => {
  if (event.type === 'WITHDRAWAL' && event.amount > 10000) {
    event.setMessage('Withdrawals over 10,000 are not allowed!')
    event.cancel()
  }
})

// Notify on large balance changes
EcocraftEvents.balanceChanged(event => {
  let diff = event.newBalance - event.oldBalance
  if (Math.abs(diff) > 1000) {
    event.player.tell(`Your balance changed by ${diff} ${event.currency}`)
  }
})

// Give bonus gold on first join (alternative to config)
// PlayerEvents.loggedIn(event => {
//   if (EcoEconomy.getBalance(event.player) === 0) {
//     EcoEconomy.deposit(event.player, 500)
//     event.player.tell('Welcome! You received 500 Gold!')
//   }
// })
