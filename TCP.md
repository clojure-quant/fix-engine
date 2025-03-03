


netstat -an |grep 5201

ss is newer
ss -tan |grep 5201


sudo tcpdump -i any port 5201 -X

more quite output in ascii
sudo tcpdump -i any port 5201 -nn -q -A

