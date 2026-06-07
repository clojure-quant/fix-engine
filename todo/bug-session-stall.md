


sometimes the connection attempt hangs on session

clojure -X:cli-trade-blotter

CONNECTION OK:
[boot] boot-with-retry: started
[boot] boot-with-retry: attempt 1
[boot] connect-and-run: begin {:host demo-us-eqx-01.p.c-trader.com, :port 5212, :ssl true}
[boot] connect-and-run: tcp connect ...
TLS-CA :  /etc/ssl/certs/ca-bundle.crt
[tcp.socket] connect {:port 5212, :host demo-us-eqx-01.p.c-trader.com, :ssl-context #object[io.netty.handler.ssl.JdkSslClientContext 0xb13f92b io.netty.handler.ssl.JdkSslClientContext@b13f92b]}
[tcp.socket] connected
[boot] connect-and-run: tcp connected, creating session
[boot] connect-and-run: session connection-id= 16stVlOR-wnJ6CpQ running ...
[fix-session] session: connecting
[fix-session] session: sending logon
[fix-session] session: waiting for logon, got :logon n= 0
[fix-session] session: logged in
[fix-session] session: requesting security-list
[fix-session] session: waiting for security-list, got :security-list n= 0
[fix-session] session: security-list ok, assets= 1915
[fix-session] session: ready, starting interactor
orderupdate: {:date #time/instant "2026-06-06T20:52:13.884Z", :limit 1.05M, :account/id 1000, :type :broker/order-confirmed, :order-id fix-1, :side :buy, :qty 1000M, :asset EURUSD, :message order confirmed}
process done nil
orderupdate: {:date #time/instant "2026-06-06T20:52:28.868Z", :limit 1.25M, :account/id 1000, :type :broker/order-confirmed, :order-id fix-2, :side :sell, :qty 1000M, :asset GBPUSD, :message order confirmed}



CONNECTION BAD:
[boot] boot-with-retry: started
[boot] boot-with-retry: attempt 1
[boot] connect-and-run: begin {:host demo-us-eqx-01.p.c-trader.com, :port 5212, :ssl true}
[boot] connect-and-run: tcp connect ...
TLS-CA :  /etc/ssl/certs/ca-bundle.crt
[tcp.socket] connect {:port 5212, :host demo-us-eqx-01.p.c-trader.com, :ssl-context #object[io.netty.handler.ssl.JdkSslClientContext 0xb13f92b io.netty.handler.ssl.JdkSslClientContext@b13f92b]}
[tcp.socket] connected
[boot] connect-and-run: tcp connected, creating session
[boot] connect-and-run: session connection-id= 16stVlOR-wnJ6CpQ running ...
[fix-session] session: connecting