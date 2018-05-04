package com.willzcode;

import com.lishid.openinv.internal.IPlayerDataManager;
import com.lishid.openinv.internal.InternalAccessor;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.*;

/**
 * Created by willz on 2018/3/15.
 * A Bukkit plugin make connection to group using coolq
 */
@SuppressWarnings({"SpellCheckingInspection", "deprecation"})
public class Chat2QQbot extends JavaPlugin {
    public static Chat2QQbot plugin;
    private IPlayerDataManager playerLoader = null;

    String servername;
    private String groupid;
    boolean enableAskyblock = false;
    private boolean enableBindPlugin = false;
    private ServerSocket serverSocket;
    private int callbackPort;

    private String checkInv(String name) {
        Player p = Bukkit.getServer().getPlayer(name);
        if (p == null && playerLoader != null) {
            p = playerLoader.loadPlayer(name);
            getLogger().info("load offline player:"+name);
        }

        String invstr = "";
        boolean hasbag = false;

        try {
            assert p != null;
            ItemStack[] itemStacks = p.getEquipment().getArmorContents();
            for (ItemStack anIa : itemStacks)
                if (anIa != null && anIa.getType() != Material.AIR)
                    invstr += ItemHelper.getShownString(anIa) + " ";
        } catch (Exception e) {
            getLogger().warning("can load Armor of player:"+name);
        }

        Inventory inventory = p.getInventory();
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

    class MessageHandleThread extends Thread
    {
        BufferedReader rdr = null;
        PrintWriter wtr = null;
        Socket sk = null;
        MessageHandleThread(Socket sk)
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
                if(payload.startsWith("001|invsee|"))
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
                        ASkyblockConnector.checkIsland(pn, true, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendToGroup("读取异常！");
                    }
                }
                else if(payload.startsWith("001|message|"))
                {
                    String[] args = payload.split("\\|");
                    if (args.length < 5) {
                        throw new StringIndexOutOfBoundsException();
                    }
                    String qq = args[2];
                    String nick = args[3];
                    String msg = args[4];
                    if (enableBindPlugin) {
                        String binded = BindPluginConnector.getBindedPlayer(qq);
                        if(binded.isEmpty())
                            Bukkit.broadcastMessage(String.format("§7[§3群消息§7]§b%s§7:§6%s", nick, msg));
                        else
                            Bukkit.broadcastMessage(String.format("§7[§3群消息§7]§b%s(%s)§7:§6%s", nick, binded, msg));
                    } else {
                        Bukkit.broadcastMessage(String.format("§7[§3群消息§7]§b%s§7:§6%s", nick, msg));
                    }

                    sendToGroup("[已发送]");
                } else if (enableAskyblock && payload.startsWith("001|container|")) {
                    String player = payload.replace("001|container|", "").replace(" ", "");
                    try {
                        ASkyblockConnector.checkContainer(player);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendToGroup("读取异常！");
                    }
                } else if (payload.startsWith("001|broadcast|")) {
                    String msg = payload.replace("001|broadcast|", "");
                    Bukkit.broadcastMessage(msg);
                } else if (payload.startsWith("001|cmd|")) {
                    String cmd = payload.replace("001|cmd|", "");
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } else if (payload.startsWith("001|stats|")) {
                    String cmd = payload.replace("001|stats|", "");
                    int ct = 0;
                    String onlines = "";
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (ct != 0) {
                            onlines += " ";
                        }
                        onlines += player.getName();
                        ct++;
                    }
                    sendToGroup(String.format("服务器在线人数：%d人\n%s", ct, onlines));
                }

            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }

    }

    @Override
    public void onDisable() {
        serverSocket = null;
    }

    @Override
    public void onEnable() {
        plugin = this;

        boolean success = InternalAccessor.Initialize(this.getServer());
        if (success) {
            playerLoader = InternalAccessor.Instance.newPlayerDataManager();
        } else {
            getLogger().warning("Your version of CraftBukkit is not supported.");
        }

        //注册事件监听
        Bukkit.getPluginManager().registerEvents(new EventListener(), this);

        initialize();

        getLogger().info("Chat2QQbot is started successfully!");
        sendToGroup("服务器已启动完成！");
    }

    private void initialize() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration cfg = getConfig();
        servername = cfg.getString("servername");
        groupid = cfg.getString("groupid");
        callbackPort = cfg.getInt("callback-port", 5700);

        if(Bukkit.getPluginManager().isPluginEnabled("ASkyBlock")) {
            enableAskyblock = true;
        }

        if(Bukkit.getPluginManager().isPluginEnabled("MustBindYourQQ")) {
            enableBindPlugin = true;
        }

        int port = cfg.getInt("port");

        final Socket[] sk = {null};
        try
        {
            if(serverSocket != null)
                try {
                    serverSocket.close();
                } catch (Exception ignored){}

            serverSocket = new ServerSocket(port);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        ServerSocket finalServer = serverSocket;
        new Thread(() -> {
            while (serverSocket != null) {
                try {
                    //每个请求交给一个线程去处理
                    sk[0] = finalServer.accept();
                    MessageHandleThread th = new MessageHandleThread(sk[0]);
                    th.start();
                    Thread.sleep(100);
                } catch (Exception e) {
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

                if(args[0].equalsIgnoreCase("inv") && args.length > 1) {
                    String player = args[1];
                    sender.sendMessage(checkInv(player));
                }

                if(args[0].equalsIgnoreCase("is") && args.length > 1) {
                    String player = args[1];
                    ASkyblockConnector.checkIsland(player, false, sender);
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

    void sendToGroup(String msg) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> post("http://localhost:" + callbackPort + "/send_group_msg?group_id=" + groupid, "message=" + msg));
    }

    private static String post(String strURL, String postData) {
        String response = "";
        try {
            //访问准备
            URL url = new URL(strURL);

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
