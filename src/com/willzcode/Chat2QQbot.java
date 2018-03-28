package com.willzcode;

import com.lishid.openinv.internal.IPlayerDataManager;
import com.lishid.openinv.internal.InternalAccessor;
import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by willz on 2018/3/15.
 */
public class Chat2QQbot extends JavaPlugin implements Listener {
    ServerSocket server = null;
    Socket sk = null;
    BufferedReader rdr = null;
    PrintWriter wtr = null;
    IPlayerDataManager playerLoader = null;

    public String checkInv(String name) {
        Player p = Bukkit.getServer().getPlayer(name);
        if (p == null && playerLoader != null) {
            p = playerLoader.loadPlayer(name);
            getLogger().info("load offline player:"+name);
        }

        String invstr = "";
        boolean hasbag = false;

        try {
            ItemStack[] ia = p.getEquipment().getArmorContents();
            for (int i = 0; i < ia.length; i++)
                if (ia[i] != null && ia[i].getType() != Material.AIR)
                    invstr += ItemHelper.getShownString(ia[i]) + " ";
        } catch (Exception e) {
            getLogger().warning("can load Armor of player:"+name);
        }

        Inventory inventory = p.getInventory();
        int size = inventory.getSize();
        for (ItemStack item:inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                if (ItemHelper.isContainer(item)) {
                    hasbag = true;
                    invstr += ItemHelper.getContainerItemsRecursively(item);
                } else
                    invstr += ItemHelper.getShownString(item) + " ";
            }
        }
        return "玩家 " + name + " 有如下物品"+(hasbag?"(已展开手提袋)":"")+"：" + invstr;
    }

    public void checkIsland(String name, boolean fromchat, CommandSender sender) {
        if (!fromchat && sender == null) {
            throw new NullPointerException();
        }
        ASkyBlockAPI asapi = ASkyBlockAPI.getInstance();

        UUID targetPlayer = Bukkit.getOfflinePlayer(name).getUniqueId();
        getLogger().info("island for " + targetPlayer);
        Island island = ASkyBlock.getPlugin().getGrid().getIsland(targetPlayer);
        World iworld = asapi.getIslandWorld();
        final HashSet chunkSnapshot = new HashSet();

        for(int x = island.getMinProtectedX(); x < island.getMinProtectedX() + island.getProtectionSize() + 16; x += 16) {
            for(int z = island.getMinProtectedZ(); z < island.getMinProtectedZ() + island.getProtectionSize() + 16; z += 16) {
                if(!iworld.getBlockAt(x, 0, z).getChunk().isLoaded()) {
                    iworld.getBlockAt(x, 0, z).getChunk().load();
                    chunkSnapshot.add(iworld.getBlockAt(x, 0, z).getChunk().getChunkSnapshot());
                    iworld.getBlockAt(x, 0, z).getChunk().unload();
                } else {
                    chunkSnapshot.add(iworld.getBlockAt(x, 0, z).getChunk().getChunkSnapshot());
                }
            }
        }

        int worldHeight = iworld.getMaxHeight();
        Bukkit.getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                String str = "玩家 " + name + " 的岛屿上有：";
                Iterator it = chunkSnapshot.iterator();

                Map<MaterialData, Integer> countMap = new HashMap<>();
                while(it.hasNext()) {
                    ChunkSnapshot chunk = (ChunkSnapshot)it.next();
                    for(int x = 0; x < 16; ++x) {
                        if (chunk.getX() * 16 + x >= island.getMinProtectedX() && chunk.getX() * 16 + x < island.getMinProtectedX() + island.getProtectionSize()) {
                            for (int z = 0; z < 16; ++z) {
                                if (chunk.getZ() * 16 + z >= island.getMinProtectedZ() && chunk.getZ() * 16 + z < island.getMinProtectedZ() + island.getProtectionSize()) {
                                    for (int y = 0; y < worldHeight; ++y) {
                                        int id = chunk.getBlockTypeId(x, y, z);
                                        MaterialData data1 = new MaterialData(id, (byte) chunk.getBlockData(x, y, z));
                                        MaterialData data2 = new MaterialData(id);
                                        int n = countMap.getOrDefault(data1, 0);
                                        countMap.put(data1, ++n);
                                    }
                                }
                            }
                        }
                    }
                }

                Iterator<Map.Entry<MaterialData, Integer>> entry = countMap.entrySet().iterator();
                while (entry.hasNext()) {
                    Map.Entry<MaterialData, Integer> e = entry.next();
                    MaterialData data = e.getKey();
                    if(data.getItemTypeId()==0)
                        continue;
                    int count = e.getValue();
                    ItemStack item = data.toItemStack(count);
                    str += ItemHelper.getShownString(item) + " ";
                }

                if (fromchat) {
                    sendMsg(str);
                } else {
                    sender.sendMessage(str);
                }
            }
        });
    }

    class ServerThread extends Thread
    {

        Socket sk = null;
        public ServerThread(Socket sk)
        {
            this.sk = sk;
        }



        public void run()
        {
            try
            {
                wtr = new PrintWriter(sk.getOutputStream());
                rdr = new BufferedReader(new InputStreamReader(sk
                        .getInputStream()));
                String msg = rdr.readLine();
                getLogger().info("Received message:" + msg);
                if(msg.indexOf("!command!")!=-1)
                {
                    String cmd = msg.replace("!command!", "");
                    Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd);
                    sendMsg("[已执行]");
                }
                else if(msg.indexOf("!invsee!")!=-1)
                {
                    String pn = msg.replace("!invsee!", "").replace(" ", "");
                    try {
                        sendMsg(checkInv(pn));
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendMsg("读取异常！");
                    }
                }
                else if(msg.indexOf("!island!")!=-1)
                {
                    String pn = msg.replace("!island!", "").replace(" ", "");
                    try {
                        checkIsland(pn, true, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendMsg("读取异常！");
                    }
                }
                else
                {
                    Bukkit.broadcastMessage(msg);
                    sendMsg("[已发送]");
                }
//                System.out.println("从客户端来的信息：" + line);
//              特别，下面这句得加上     “\n”,
//                wtr.println("你好，服务器已经收到您的信息！'" + line + "'\n");
//                wtr.flush();
//                System.out.println("已经返回给客户端！");
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }

    }

    @Override
    public void onEnable() {
        boolean success = InternalAccessor.Initialize(this.getServer());
        if (success) {
            playerLoader = InternalAccessor.Instance.newPlayerDataManager();
        } else {
            getLogger().warning("Your version of CraftBukkit is not supported.");
        }

        getLogger().info("Chat2QQbot is started successfully!");
        //注册监听
        Bukkit.getPluginManager().registerEvents(this, this);

        try
        {
            server = new ServerSocket(6801);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        new Thread(){
            @Override
            public void run() {

                while (true)
                {
                    try
                    {
//                  每个请求交给一个线程去处理
                        sk = server.accept();
                        ServerThread th = new ServerThread(sk);
                        th.start();
                        sleep(100);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                }
            }
        }.start();
//        new BukkitRunnable() {
//            @Override
//            public void run() {
////                getLogger().info("Bukkit runner runing!");
//                try
//                {
//                    //1.创建客户端Socket，指定服务器地址和端口
//                    Socket socket=new Socket("localhost", 6801);
////                    //2.获取输出流，向服务器端发送信息
////                    OutputStream os=socket.getOutputStream();//字节输出流
////                    PrintWriter pw=new PrintWriter(os);//将输出流包装为打印流
////                    if(player!="none233")
////                    {
////                        pw.write(msg);
////                        player="none233";
////                    }
////                    else
////                    {
////                        pw.write("getmsg");
////                    }
////                    pw.flush();
////                    socket.shutdownOutput();//关闭输出流
//                    //3.获取输入流，并读取服务器端的响应信息
//                    InputStream is=socket.getInputStream();
//                    BufferedReader br=new BufferedReader(new InputStreamReader(is));
//                    String info=null;
//                    info=br.readLine();
//                    getLogger().info("Received message:" + info);
//                    if(info.indexOf("!command!")!=-1)
//                        {
//                            String cmd = info.replace("!command!", "");
//                            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd);
//                            sendMsg("指令 "+cmd+" 已执行");
//                        }
//                        else
//                        {
//                            Bukkit.broadcastMessage(info);
//                            sendMsg("消息已发送");
//
////                            if(player!="none233")
////                            {
////                                msg+="]][[<提示>"+sourceStrArray[i].replace("command>", "")+"已执行";
////                            }
////                            else
////                            {
////                                player="ok";
////                                msg="<提示>"+sourceStrArray[i].replace("command>", "")+"已执行";
////                            }
//                        }
//                    //Bukkit.broadcastMessage("debug:"+info);
//                    //4.关闭资源
//                    br.close();
//                    is.close();
//                    socket.close();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }.runTaskTimer(this, 0L, 20L);
        sendMsg("服务器已启动完成");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(label.equals("fuck"))
        if (sender.isOp() && args.length > 0) {
            String p = args[0];
            getLogger().info("fuck a player: " + p);
            if(Bukkit.getOfflinePlayer(p).isOnline()){
                Player player = Bukkit.getPlayer(p);
                player.kickPlayer("You are fucked!");
                player.setBanned(true);
            }else{
                OfflinePlayer player = Bukkit.getOfflinePlayer(p);
                player.setBanned(true);
            }
        }

        if(label.equals("cq"))
            if (args.length > 0) {
                sender.sendMessage(checkInv(args[0]));
            }
        return true;
    }

    @Override
    public void onDisable()
    {
        getLogger().info("Chat2QQbot is stoped successfully!");
    }

    @EventHandler
    public void onPlayerSay(AsyncPlayerChatEvent event)
    {
        if(!event.isCancelled()) {
            long lvl = ASkyBlockAPI.getInstance().getLongIslandLevel(event.getPlayer().getUniqueId());
            sendMsg("[空岛" + lvl + "级]" + event.getPlayer().getName() + ":" + event.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        sendMsg("一服: "+event.getPlayer().getName()+" 上线了");
        //sendMsg(checkInv(event.getPlayer().getName()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        sendMsg("一服: "+event.getPlayer().getName()+" 下线了");
    }

    public void sendMsg(String msg) {
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                String res = post("http://localhost:5701/send_group_msg?group_id=597884379", "message="+msg);
            }
        });
    }

    /**
     * 发起http请求获取返回结果
     * @param req_url 请求地址
     * @return
     */
    public static String httpRequest(String req_url) {
        StringBuffer buffer = new StringBuffer();
        try {
            URL url = new URL(req_url);
            HttpURLConnection httpUrlConn = (HttpURLConnection) url.openConnection();

            httpUrlConn.setDoOutput(false);
            httpUrlConn.setDoInput(true);
            httpUrlConn.setUseCaches(false);

            httpUrlConn.setRequestMethod("GET");
            httpUrlConn.connect();

            // 将返回的输入流转换成字符串
            InputStream inputStream = httpUrlConn.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String str = null;
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputStreamReader.close();
            // 释放资源
            inputStream.close();
            inputStream = null;
            httpUrlConn.disconnect();

        } catch (Exception e) {
            System.out.println(e.getStackTrace());
        }
        return buffer.toString();
    }


    /**
     * 发送HttpPost请求
     *
     * @param strURL
     *            服务地址
     * @param
     *
     * @return 成功:返回json字符串<br/>
     */
    public static String post(String strURL, String postData) {
        String response = "";
        try {
            //访问准备
            URL url = new URL(strURL);

            //开始访问
//            StringBuilder postData = new StringBuilder();
//            for (Map.Entry<String, Object> param : params.entrySet()) {
//                if (postData.length() != 0) postData.append('&');
//                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
//                postData.append('=');
//                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
//            }
//            byte[] postDataBytes = postData.toString().getBytes("UTF-8");
            byte[] postDataBytes = postData.getBytes("UTF-8");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            conn.setDoOutput(true);
            conn.getOutputStream().write(postDataBytes);

            Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (int c; (c = in.read()) >= 0; )
                sb.append((char) c);
            response = sb.toString();
            System.out.println(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }
}
