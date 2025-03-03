


netstat -an |grep 5201

ss is newer
ss -tan |grep 5201


sudo tcpdump -i any port 5201 -X

more quite output in ascii
sudo tcpdump -i any port 5201 -nn -q -A

wc -l msg.log
ag -C 10 "watchdog" msg.log


app start         20250303-04:44:48.364
watchdog timeout  20250303-05:28:57.023 seqno 69219  line 138677
session reconnect 20250303-13:57:23.293 
app stop line 164345
