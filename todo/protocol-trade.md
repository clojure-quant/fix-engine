


I want to make fix-engine trade functionality independent of the api implementation,
similar to how the quote functionality works.


1. quanta.blotter.interactor currently uses
blotter-order->fix-payload and fix-payload->blotter-update
Instead it should use quanta.blotter.protocol2/trade-messaging






