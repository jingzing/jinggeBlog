**参考地址：[mysql高可用架构之MHA，haproxy实现读写分离详解](http://www.cnblogs.com/dannylinux/p/8033318.html)**

###### **实验步骤：**

> **ssh证书户信任（ssh的密钥验证）**

> **ABB架构**

> **安装mha_manager  、 mha_node**

> **测试**

###### **IP地址规划:**

> **Mysql_manager(10.211.55.23)—需要是64位的系统**

> **MySQL_A(10.211.55.20)**

> **MySQL_B1(10.211.55.21)**

> **MySQL_B2(10.211.55.22)**

------

### ssh证书互信脚本---每台机器上都要操作

#### 使用ssh-kegen生成公私钥（每台服务器上），下面是批量执行脚本：
<pre><code>
#!/bin/bash
  for i in 0 1 2 3
​        do
​                ssh-copy-id -i /root/.ssh/id_rsa.pub 10.211.55.20$i
​        done
</code></pre>

### ABB搭建

#### Mysql 的安装(感觉下面的安装方法比较简单):

  <pre><code>
   	wget http://dev.mysql.com/get/mysql-community-release-el7-5.noarch.rpm
   	rpm -ivh mysql-community-release-el7-5.noarch.rpm
  	yum install mysql-community-server
	service mysqld restart
   	mysql -u root
   	mysql> set password for 'root'@'localhost' =password('youpassword');
   </code></pre>

#### 所有的机器初始化MySQL，设置ABB架构：

> **MYSQL_A(20)上的操作：**
>
> **vim /etc/my.cnf**
>
> **server_id=1       //设置优先级最高**
>
> **log_bin=binlog　　//开启binlog日志**
>
> **log_biin=binlog.index**
>
> **<u>注意：MYSQL_B1、MYSQL_B2上的操作同mysql_a只是注意server_id的不同！</u>**

#### 设置用户

**在msyql_a(20)中：因为前面已经开启二进制日志了，所以其他机器也能学到赋权语句！！故其他机器就不用再赋权了！**

  <pre><code>
   	//给mha_manager用，因为其在failover时需要登陆上来，并且拷贝binlog到所有的slave上去
   	jdata1> grant all on *.* to 'root'@'%' identified by 'youpassword';
   	//复制专用用户
   	jdata1> grant replication slave on *.* to 'sky'@'%' identified by 'youpassword';
   	//刷新权限
   	jdata1> flush privileges;  
   	//看一下binlog日志到第几个文件了　　
   	jdata1> show master status\G 
  </code></pre>

**在mysql_b1(21)中：**

  <pre><code>
      mysql> slave stop;
​     mysql> change master to master_host="10.211.55.20",master_user="sky",master_passoword="yourpassword",master_log_file=”mysql-bin.000003”;
​     mysql> reset slave; (注意:如果之前因为同步数据的时候sql执行错误，这里要清除掉)
​     mysql> slave start;
​     mysql> show slave status\G        //查看复制线程状态
  </code></pre>

**<u>在mysql_b2（22）做21上同样的操作！</u>**



### 安装配置启动mha_manager、mha_node

**manager机器上（23）：cd /root/zing (mha4mysql-node 和 mha4mysql-manager rpm 文件,要保存这2个文件，这2个东西不好找，下面提供下载地址)**

网盘链接：[mha软件下载](https://pan.baidu.com/s/1-lFvROxmIz2AxahO61qE4Q) 

在管理节点安装MHA管理组件，先安装node再安装manager软件本身有依赖关系

`yum install mha4mysql-node-0.56-0.el6.noarch.rpm`

`yum install mha4mysql-manager-0.56-0.el6.noarch.rpm`

manager 需要一下包的依赖：

在**<u>node节点</u>**上，执行下面的命令：`yum install perl-DBD-MySQL`

在**<u>manager节点</u>**上：

  <pre><code>
    yum install perl-DBD-MySQL
    yum install perl-Config-Tiny
    yum install perl-Log-Dispatch
    yum install perl-Parallel-ForkManager
    yum install -y rrdtool perl-rrdtool rrdtool-devel perl-Params-Validate
  </pre></code>

***坑点1：发现 yum 不能安装perl-Log-Dispatch 和   perl-Parallel-ForkManager:***

解决方案：[CentOS7安装第三方yum源EPEL](https://www.linuxidc.com/Linux/2015-10/124002.htm ) 

下载并安装EPEL

  <pre><code>
   	[root@localhost ~]# wget http://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
   	[root@localhost ~]# rpm -ivh epel-release-latest-7.noarch.rpm
  	[root@localhost ~]# yum repolist      ##检查是否已添加至源列表
   </code></pre>

OK，检查好已添加至源后就可以进行yum安装了。

**最后，将mha4mysql-node包复制到集群中的所有节点：**

  <pre><code>
   	for i in 1 2 3 ;
   	do
  	scp mha4mysql-node-0.56-0.el6.noarch.rpm 10.211.55.2$i:/usr/src;
  	done
   </code></pre>

**mha_node: 安装到其他node(所有其他机器)**

`[root@MHA_240 mha_soft]# yum install -y mha4mysql-node-0.54-0.el6.noarch.rpm`

**mha_manager: (MHA 20上)**

`[root@MHA_240 mha]# cp -pr /usr/src/mha_soft/mha/ /etc/       //mha的配置文件和启动`

**<u>文件（注意：这里我是没有找到mha.cnf 和 mha_start，vi 创建mha.cnf文件）</u>**

配置如下：

  <pre><code>
	[server default]

​	#mysql admin account and password

​	user=root

​	password=62661206

​	#mha workdir and worklog

​	manager_workdir=/etc/mha

​	manager_log=/etc/mha/manager.log

​	#mysql A/B account and pw

​	repl_user=sky

​	repl_password=62661206

​	#check_mha_node time

​	ping_interval=1

​	#ssh account

​	ssh_user=root

​	[server1]

​	hostname=10.211.55.20

​	ssh_port=22

​	master_binlog_dir=/var/lib/mysql

​	candidate_master=1

​	[server2]

​	hostname=10.211.55.21

​	ssh_port=22

​	master_binlog_dir=/var/lib/mysql

​	candidate_master=1

​	[server3]

​	hostname=10.211.55.22

​	ssh_port=22

​	master_binlog_dir=/var/lib/mysql

​	candidate_master=1
   </code></pre>

 **检测ssh互信有没有问题**

`[root@localhost src]# masterha_check_ssh --conf=/etc/mha/mha.cnf`

`Tue Jun 13 02:21:31 2017 - [info] All SSH connection tests passed successfully. 有这个表示成功`

**测AB**
   <code><pre>
​	[root@localhost src]# masterha_check_repl --conf=/etc/mha/mha.cnf
​	…
​	MySQL Replication Health is OK.
   </code></pre>

***坑点2：masterha_check_repl 检测没有通过，报下面错误：***

[![检测错误](http://www.wailian.work/images/2018/04/20/1error.jpg)](http://www.wailian.work/image/epe1Ue)

[MHA 报错：There is no alive slave. We can't do failover](http://www.mamicode.com/info-detail-2089525.html) 

> 关于该问题，比较靠谱的解释是：（我的问题是没有关防火墙）

MHA默认去连接MySQL的端口是：3306

如果你的主机名解析，或者你写的IP都没问题**<u>，防火墙也关闭了</u>**，那么，剩下的原因是：

你的MySQL，没有运行在默认端口上。

如果不能修改MySQL的端口为：3306。

那么你可以给MHA，添加PORT描述。

> vi /etc/mha/mha_start       //编辑mha启动脚本，直接运行下面启动也可以

`nohup masterha_manager --conf=/etc/mha/mha.cnf > /tmp/mha_manager.log 2>&1 &`

------



#### 测试

**现在两个slave的Master_Host同为241，把241干掉后，就会选举新的master了！**

> 先在mha_manager上打开日志：
>
> tail -f /etc/mha/manager.log
>
> 到20上关闭MySQL服务：
>
> service mysqld stop
>
> 查看mha_manager日志输出：

 <code><pre>

----- Failover Report ----- 

mha: MySQL Master failover 10.211.55.20 to 10.211.55.21 succeeded 

Master 10.211.55.21 is down!

Check MHA Manager logs at localhost.localdomain:/etc/mha/manager.log for details.

Started automated(non-interactive) failover.

The latest slave 10.211.55.21(10.211.55.21:3306) has all relay logs for recovery.

Selected 10.211.55.21 as a new master.

10.211.55.21: OK: Applying all logs succeeded.

10.211.55.21: This host has the latest relay log events.

Generating relay diff files from the latest slave succeeded.

10.211.55.21: OK: Applying all logs succeeded. Slave started, replicating from 10.211.55.21.

10.211.55.21: Resetting slave info succeeded.

Master failover to 10.211.55.20(10.211.55.21:3306) completed successfully.

</code></pre>

**看来21变成了master！~**

**可以去新主上创建个库或到21上查看一下master.info来验证！！**

<!--老master恢复后如果想要它再做master，要先将新master的数据同步，之后删除下面两个文件，再重新开启MHA功能，令新master宕掉即可（实验可行，实际操作不推荐）如下：-->

<!--注意：mha_manager每执行一次failover后，该进程自动退出。如果还想测试failover需要重新开启---开启前要将下面两个文件删掉：-->

<!--[root@jdata4 mha]# cd /etc/mha/-->

<!--[root@jdata4  mha]# rm -fr mha.failover.complete saved_master_binlog_from_10.211.55.21_3306_20180417154913.binlog-->



**<u>下面演示old_master回来，如何保证old_master同步new_master的新产生的数据：</u>**

**当old_master服务宕掉后，去mha_monitor上执行：**

**[root@mha_master mha]# grep -i change /etc/mha/manager.log (-i 是不区分大小写)**

**Tue Jun 13 02:30:23 2018 - [info]  All other slaves should start replication from here. Statement should be: CHANGE MASTER TO MASTER_HOST='10.211.55.21', MASTER_PORT=3306, MASTER_LOG_FILE='binlog.000001', MASTER_LOG_POS=106, MASTER_USER='sky', MASTER_PASSWORD='xxx';**

**然后在old_master上执行：**

 <code><pre>

mysql> slave stop;
mysql> change master to  master_host='10.211.55.21', master_port=3306, master_log_file='binlog.000001', master_log_pos=106, master_user='sky', master_password='youpassword';(只需要修改密码即可)
mysql> slave start;

 </code></pre>

***坑点3：mysql 的密码忘记了，重置密码的解决方案：***

[重置密码解决MySQL for Linux错误 ERROR 1045 (28000)](https://www.cnblogs.com/gumuzi/p/5711495.html)

方法如下：

`vim /etc/my.cnf(注：windows下修改的是my.ini)`

在文档内搜索mysqld定位到[mysqld]文本段：

/mysqld(在vim编辑状态下直接输入该命令可搜索文本内容)

保存文档并退出：

`:wq`

2.接下来我们需要重启MySQL：

`service mysqld restart`

3.重启之后输入#mysql即可进入mysql。

4.接下来就是用sql来修改root的密码

`mysql> use mysql;`

`mysql> update user set password=password("你的新密码") where user="root";`

`mysql> flush privileges;`

`mysql> quit`

到这里root账户就已经重置成新的密码了。

5.编辑my.cnf,去掉刚才添加的内容，然后重启MySQL。大功告成。



***坑点4：&>/dev/null表示的意思***

[/dev/null表示的意思](https://blog.csdn.net/long2746004900/article/details/53367353)

**用 /dev/null 2>&1 这样的写法.这条命令的意思是将标准输出和错误输出全部重定向到/dev/null中,也就是将产生的所有信息丢弃.**

------

#### MHA failover(master故障)后VIP漂移

**MHA架构中，master来承担写请求，但是如果发生了failover，那么就应该让new_master来承担写请求，有哪些方式可以实现呢？**

1. 改变master的IP：在web上修改PHP页面的代码（所有写请求修改成new_master的IP）
2. 使用虚拟IP（VIP），将VIP漂移给new_master

**显然，第二种方案要更加容易实现、高效。**

**实现起来，大家可能会首当其冲的想到keepalived，但是在这里不适用，因为我们不好判断哪一个salve会在failover后变成master（在keepalived中，VIP根据物理路由器的优先级来确定，万一漂到一台slave上那可如何是好！）。不过我们可以通过脚本的方式来实现将VIP绑定到new_master上。**

**脚本思路如下：**

> ​       脚本（/etc/mha/check_mysql）运行在manager上，它来管理VIP

> ​       判断谁是主，确保它有VIP，并继续判断，如果slave有VIP，则收回。

**脚本名称：master_vip_drift.sh(<u>如果不是salve 的话，|grep -w 'Slave_IO_Running'应该是错误的不是1</u>)**

 <code><pre>
#!/bin/bash
VIP=10.211.55.23
NETMASK=255.255.255.0
MUSER=root
MPW=62661206
MYSQL_IP="10.211.55.20 10.211.55.21 10.211.55.22"
NIC=eth0
check_master(){
  for IP in $MYSQL_IP
​    do
​      if ssh $IP "lsof -i :3306" &>/dev/null;then
​      ssh $IP "mysql -uroot -p62661206 -e 'show slave status \G'|grep -w 'Slave_IO_Running'" &>/dev/null
​        echo "$#"
​        if [ $? -eq 1 ];then
​                MY_master=$IP
​                echo "$MY_master"
​        fi
​      fi
done
}

check_master_alive(){
  for IP in $MYSQL_IP
​    do
​      if ssh $IP "ip add show eth0"|grep inet|grep "$VIP" &>/dev/null;then
​    ssh $IP "lsof -i:3306" &>/dev/null
​      if [ $? -ne 0 ];then
​    ssh $IP "ifconfig $NIC:23 down "
​      fi
​      fi
done
}

VIP() {
  for IP in $MYSQL_IP
​    do
​      ssh $IP "ip add show eth0"|grep inet|grep "$VIP" &>/dev/null
​      if [ $? -eq 0 ] && [[ $MY_master != "$IP" ]];then
​      ssh $IP "ifconfig $NIC:23 down"
​      elif [ $? -eq 1 ] && [[ $MY_master == "$IP" ]];then
​      ssh $IP "ifconfig $NIC:23 $VIP netmask $NETMASK up"
​      fi
done
}

while true
   do
​    check_master
     check_master_alive
     VIP
​     sleep 1
done
 </code></pre>

***坑点5：shell脚本报错："[: =: unary operator expected"***

[参考链接shell脚本报错](https://blog.csdn.net/goodlixueyong/article/details/6564591)

**shell脚本报错："[: =: unary operator expected"**

 究其原因，是因为如果变量STATUS值为空，那么就成了 [ = "OK"] ，显然 [ 和 "OK" 不相等并且缺少了 [ 符号，所以报了这样的错误。当然不总是出错，如果变量STATUS值不为空，程序就正常了，所以这样的错误还是很隐蔽的。

**改成 if [[ $STATUS = "OK" ]];** 

##### 分析failover日志—了解MHA工作流程

1. tailf /etc/mha/manager.log
2. 到master上关闭MySQL服务
3. 查看日志并从头往下捋一遍

------

#### MySQL MHA读写分离

> **Mysql_manager和HAProxy(10.211.55.23)**

> **MySQL_A(10.211.55.20)**

> **MySQL_B1(10.211.55.21)**

> **MySQL_B2(10.211.55.22)**

![image1.png](http://www.wailian.work/images/2018/04/25/image1.png)

**结构图说明：**

> **a.MHA对abb架构实行监控，发现a宕之后选出新a（实现VIP漂移，仅a有VIP）**
>
> **b.haproxy对slave集群实现负载均衡，承担读请求**
>
> **c.web集群请求后台数据，写请求传给a(VIP)，读请求传给haproxy，haproxy再对读请求实现分发达到负载均衡**

#####   安装HAProxy(10.211.55.23)

网盘链接：[mha软件下载](https://pan.baidu.com/s/1-lFvROxmIz2AxahO61qE4Q) 

 <code><pre>
yum install gcc -y
tar xf haproxy-1.5.3.tar
make TARGET=linux26 PREFIX=/usr/local/haproxy
make install PREFIX=/usr/local/haproxy
cp /usr/src/haproxy/haproxy-1.5.3/examples/haproxy.cfg /usr/local/haproxy/
cp /usr/src/haproxy/haproxy-1.5.3/examples/haproxy.init /etc/init.d/haproxy
chmod 755 /etc/init.d/haproxy
ln -s /usr/local/haproxy/sbin/* /usr/sbin/
mkdir /etc/haproxy
mkdir /usr/share/haproxy
ln -s /usr/local/haproxy/haproxy.cfg /etc/haproxy/
cd ..
 </code></pre>

***设置配置文件***

`[root@HAProxy_247 haproxy]# cp haproxy.cfg  /usr/local/haproxy/`

***编辑配置文件***

`[root@HAProxy_247 haproxy]# vim /usr/local/haproxy/haproxy.cfg`

 <code><pre>
# this config needs haproxy-1.1.28 or haproxy-1.2.1
global
    log 127.0.0.1    local0
    log 127.0.0.1    local1 notice
    #log loghost    local0 info
    maxconn 4096
    chroot /usr/share/haproxy
    uid 99
    gid 99
    daemon
    #debug
    #quiet
defaults
    log    global
    mode    http
    #option    httplog
    option    dontlognull
    retries    3
    option redispatch
    maxconn    2000
    contimeout    5000
    clitimeout    50000
    srvtimeout    50000
listen    MySQL 0.0.0.0:3306
    mode tcp
    maxconn    200
    balance roundrobin
    option mysql-check user root
    server mysql_1 10.211.55.21:3306  inter 1s rise 2 fall 2
    server mysql_2 10.211.55.22:3306  inter 1s rise 2 fall 2
listen  admin_status
        mode  http
        bind 0.0.0.0:8899
        option httplog
        log global
        stats enable
        stats refresh 10s
        stats hide-version
        stats realm Haproxy\ Statistics
        stats uri  /admin-status 
        stats auth  admin:123456 
        stats admin if TRUE
 </code></pre>

***启动HAProxy服务***

`[root@HAProxy_247 haproxy]# service haproxy start`

**访问HAProxy的网页(用户名密码查看配置文件舍子)**

`http://localhost:8899/admin-status`

**客户端测试：**

`mysql -uroot -pyoupassword -h 10.211.55.23`

**在HAProxy管理界面，可以看到那台slave响应的请求**

![image2.png](http://www.wailian.work/images/2018/04/25/image2.png)