package com.willzcode;

import com.lishid.openinv.internal.IPlayerDataManager;
import com.lishid.openinv.internal.InternalAccessor;
import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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
    IPlayerDataManager playerLoader = null;

    String servername;
    int port;
    String groupid;
    boolean enableAskyblock;
    boolean enableBindPlugin;

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
                    sendToGroup(str);
                } else {
                    sender.sendMessage(str);
                }
            }
        });
    }

    class ServerThread extends Thread
    {
        BufferedReader rdr = null;
        PrintWriter wtr = null;
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
                String payload = rdr.readLine();
                getLogger().info("Received message:" + payload);
                if(payload.indexOf("001|invsee|")!=-1)
                {
                    String pn = payload.replace("001|invsee|", "").replace(" ", "");
                    try {
                        sendToGroup(checkInv(pn));
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendToGroup("读取异常！");
                    }
                }
                else if(enableAskyblock && payload.startsWith("001|island|"))
                {
                    String pn = payload.replace("001|island|", "").replace(" ", "");
                    try {
                        checkIsland(pn, true, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendToGroup("读取异常！");
                    }
                }
                else if(enableAskyblock && payload.startsWith("001|message|"))
                {
                    String[] args = payload.split("\\|");
                    if (args.length < 5) {
                        throw new StringIndexOutOfBoundsException();
                    }
                    String qq = args[2];
                    String nick = args[3];
                    String msg = args[4];
                    if (enableBindPlugin) {

                        String binded = MustBindYourQQ.plugin.getQQBinded(qq);
                        if(binded.isEmpty())
                            Bukkit.broadcastMessage(String.format("§7[§3群消息§7]§b%s§7:§6%s", nick, msg));
                        else
                            Bukkit.broadcastMessage(String.format("§7[§3群消息§7]§b%s(%s)§7:§6%s", nick, binded, msg));
                    } else {
                        Bukkit.broadcastMessage(String.format("§7[§3群消息§7]§b%s§7:§6%s", nick, msg));
                    }

                    sendToGroup("[已发送]");
                } else if (payload.startsWith("001|broadcast|")) {
                    String msg = payload.replace("001|broadcast|", "");
                    Bukkit.broadcastMessage(msg);
                }

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

        //注册事件监听
        Bukkit.getPluginManager().registerEvents(this, this);

        initialize();

        getLogger().info("Chat2QQbot is started successfully!");
        sendToGroup("服务器已启动完成！");
    }

    void initialize() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration cfg = getConfig();
        servername = cfg.getString("servername");
        port = cfg.getInt("port");
        groupid = cfg.getString("groupid");
        enableAskyblock = cfg.getBoolean("enable-askyblock");
        enableBindPlugin = cfg.getBoolean("enable-bind-plugin");

        ServerSocket server = null;
        final Socket[] sk = {null};
        try
        {
            if(server != null)
                server.close();
            server = new ServerSocket(port);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        ServerSocket finalServer = server;
        new Thread(() -> {

            while (true)
            {
                try
                {
                    //每个请求交给一个线程去处理
                    sk[0] = finalServer.accept();
                    ServerThread th = new ServerThread(sk[0]);
                    th.start();
                    Thread.sleep(100);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(sender.isOp() && label.equals("cq"))
            if (args.length > 0) {
                if(args[0].equalsIgnoreCase("reload")) {
                    initialize();
                    sender.sendMessage("配置重载!");
                }

                if (args[0].equalsIgnoreCase("msg") && args.length > 1) {
                    StringBuilder msg = new StringBuilder();
                    for(int i = 1; i < args.length; i++) {
                        if(i > 1)
                            msg.append(' ');
                        msg.append(args[i]);
                    }
                    sendToGroup(msg.toString());
                }
            }
        return true;
    }

    @EventHandler
    public void onPlayerSay(AsyncPlayerChatEvent event)
    {
        if(!event.isCancelled()) {
            if(enableAskyblock) {
                long lvl = ASkyBlockAPI.getInstance().getLongIslandLevel(event.getPlayer().getUniqueId());
                sendToGroup(String.format("[空岛%s级]%s:%s", lvl, event.getPlayer().getName(), event.getMessage()));
            }
            else
                sendToGroup(String.format("%s:%s", event.getPlayer().getName(), event.getMessage()));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        sendToGroup(String.format("[%s] %s 上线了", servername, event.getPlayer().getName()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sendToGroup(String.format("[%s] %s 下线了", servername, event.getPlayer().getName()));
    }

    public void sendToGroup(String msg) {
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                String res = post("http://localhost:5701/send_group_msg?group_id="+groupid, "message="+msg);
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
