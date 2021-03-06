package is.gussi.bukkit.plugin.sandkassinn;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import is.gussi.bukkit.plugin.sandkassinn.bans.BanData;
import is.gussi.bukkit.plugin.sandkassinn.bans.Datasource;
import is.gussi.bukkit.plugin.sandkassinn.bans.datasource.DatasourceMySQL;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class Bans implements Listener {
	public Datasource ds;

	public Bans(Sandkassinn plugin) {
		
		// Check if enabled, disable silently if not
		if(!Sandkassinn.plugin.getConfig().getBoolean("sandkassinn.modules.bans.enabled")) {
			return;
		}

		// Check if perm service is set, disable if not
		if(Sandkassinn.perms == null) {
			Sandkassinn.log.warning("Disabling bans module - missing permissions.");
			return;
		}
		
		// Register datasource
		// TODO: Multiple datasources?
		this.ds = new DatasourceMySQL();

		// Register events
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		
		// Tempban command handler
		plugin.getCommand("tempban").setExecutor(new CommandExecutor() {
			@SuppressWarnings("serial")
			@Override
			public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
				// Two arguments is a must, we can work up from there
				if (args.length < 2) {
					return false;
				}

				// Set ban data
				BanData data = null;
				try {
					data = new BanData(getPlayer(args[0], sender).getName(), BanData.Type.TEMPBAN, getReason(args, 2), sender.getName(), getTime(args[1], sender));
					if (!data.sanityCheck()) return false;
				} catch(Exception e) {
					return false;
				}
				
				// Check if already banned
				// TODO: Remove code dupe
				final BanData data_current = ds.check(data.banned);
				DateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
				if (data_current != null) {
					switch (data_current.type) {
						case PERMABAN:
							// TODO: i18n
							sender.sendMessage(buildString("&4{banned} &cer n� thegar endanlega bannadur af &4{executor} &cvegna &4{reason}", new HashMap<String, String>(){{
								put("banned", data_current.banned);
								put("executor", data_current.executor);
								put("reason", data_current.reason);
							}}));
							return true;
						case TEMPBAN:
							if(data_current.date_expire*1000 < System.currentTimeMillis()) {
								break;
							}
							// TODO: i18n
							sender.sendMessage(buildString("&4{banned} &cer n� thegar bannadur til &4{date_expire} &caf &4{executor} &cvegna &4{reason}", new HashMap<String, String>(){{
								put("banned", data_current.banned);
								put("date_expire", String.valueOf(data_current.date_expire));
								put("executor", data_current.executor);
								put("reason", data_current.reason);
							}}));
							return true;
					}
				}
				
				// Add data and broadcast message
				// TODO: Remove code dupe
				ds.add(data);
				Date date = new Date(data.date_expire*1000);
				
				// TODO: i18n
				Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + sender.getName() + ChatColor.RED + " bannadi " + ChatColor.DARK_RED + data.banned + ChatColor.RED + " til " + ChatColor.DARK_RED + fmt.format(date) + ChatColor.RED + " vegna " + ChatColor.DARK_RED + data.reason);
				OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(args[0]);
				if (player.isOnline()) {
					((Player)player).kickPlayer("Bannadur til " + fmt.format(date) + " vegna: " + data.reason);
				}
				return true;
			}
		});

		// Ban command handler
		plugin.getCommand("ban").setExecutor(new CommandExecutor() {
			@Override
			public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
				// One argument is a must, we can work up from there
				if (args.length < 1) {
					return false;
				}
				
				// Set ban data
				BanData data = null;
				try {
					data = new BanData(getPlayer(args[0], sender).getName(), BanData.Type.PERMABAN, getReason(args, 1), sender.getName(), 0L);
					if (!data.sanityCheck()) return false;
				} catch(Exception e) {
					return false;
				}
				
				// Check if already banned
				// TODO: Remove code dupe
				DateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
				BanData data_current = ds.check(data.banned);
				if (data_current != null) {
					switch (data_current.type) {
						case PERMABAN:
							// TODO: i18n
							sender.sendMessage(data_current.banned + " er n� thegar endanlega bannadur af " + data_current.executor + " vegna " + data_current.reason);
							return true;
						case TEMPBAN:
							if(data_current.date_expire*1000 < System.currentTimeMillis()) {
								break;
							}
							// TODO: i18n
							sender.sendMessage(data_current.banned + " er n� thegar bannadur til " + fmt.format(new Date(data_current.date_expire))  + " af " + data_current.executor + " vegna " + data_current.reason);
							return true;
					}
				}
					
				
				// Add data and broadcast message
				// TODO: Remove code dupe
				ds.add(data);
				// TODO: i18n
				Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + sender.getName() + ChatColor.RED + " bannadi " + ChatColor.DARK_RED + data.banned + ChatColor.RED + " endanlega vegna " + ChatColor.DARK_RED + data.reason);
				OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(args[0]);
				if (player.isOnline()) {
					((Player)player).kickPlayer("Endanlegt bann vegna: " + data.reason);
				}
				
				return true;
			}
		});

		// Unban command handler
		plugin.getCommand("unban").setExecutor(new CommandExecutor() {
			@Override
			public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
				// One argument is a must, we can work up from there
				if (args.length < 1) {
					return false;
				}
				
				// Set unban data
				BanData data = new BanData(getPlayer(args[0], sender).getName(), BanData.Type.PARDON, getReason(args, 1), sender.getName(), 0L);
				if (!data.sanityCheck()) return false;
				
				BanData data_old = ds.check(data.banned);
				if (data_old == null) {
					// TODO: i18n
					sender.sendMessage(ChatColor.DARK_RED + data.banned + ChatColor.RED + " er ekki bannadur");
					return true;
				}
				
				Date date_executed = new Date(data_old.date_executed*1000);
				Date date_expire = new Date(data_old.date_expire*1000);
				DateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
				switch(data_old.type) {
					case PARDON:
						// TODO: i18n
						sender.sendMessage(ChatColor.DARK_RED + data.banned + " var unbannadur af " + data_old.executor + " thann " + fmt.format(date_executed));
						break;
					case PERMABAN:
						// TODO: i18n
						sender.sendMessage(ChatColor.DARK_RED + data.banned + " var endanlega bannadur af " + data_old.executor + " thann " + fmt.format(date_executed) + " vegna " + data_old.reason);
						// TODO: i18n
						ds.add(data);
						Bukkit.getServer().broadcastMessage(ChatColor.DARK_GREEN + data.banned + " var unbannadur af " + data.executor + " vegna: " + data.reason);
						break;
					case TEMPBAN:
						// TODO: i18n
						sender.sendMessage(ChatColor.DARK_RED + data.banned + ChatColor.RED + " var bannadur t�mabundid af " + ChatColor.DARK_RED + data_old.executor + ChatColor.RED + " thann " + ChatColor.DARK_RED + fmt.format(date_executed) + ChatColor.RED + " til " + ChatColor.DARK_RED + fmt.format(date_expire) + ChatColor.RED + " vegna " + ChatColor.DARK_RED + data_old.reason);
						ds.add(data);
						// TODO: i18n
						Bukkit.getServer().broadcastMessage(ChatColor.DARK_GREEN + data.banned + " var unbannadur af " + data.executor + " vegna " + data.reason);
						break;
					case WARNING:
						// TODO: i18n
						sender.sendMessage(ChatColor.DARK_RED + data.banned + ChatColor.RED + " var warned af " + ChatColor.DARK_RED + data_old.executor + ChatColor.RED + " thann " + ChatColor.DARK_RED + fmt.format(date_executed) + ChatColor.RED + " til " + ChatColor.DARK_RED + fmt.format(date_expire) + ChatColor.RED + " vegna " + ChatColor.DARK_RED + data_old.reason);
						ds.add(data);
						// TODO: i18n
						Bukkit.getServer().broadcastMessage(ChatColor.DARK_GREEN + data.banned + " var unbannadur af " + data.executor + " vegna " + data.reason);
						break;
				}
				return true;
			}
		});
		
		// Warn command handler
		plugin.getCommand("warning").setExecutor(new CommandExecutor() {
			@Override
			public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
				// Three arguments is a must
				if (args.length < 3) {
					return false;
				}
				
				// Set warning data
				BanData data = new BanData(getPlayer(args[0], sender).getName(), BanData.Type.WARNING, getReason(args, 2), sender.getName(), getTime(args[1], sender));
				if (!data.sanityCheck()) return false;
				
				ds.add(data);
				// TODO: i18n
				Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + sender.getName() + ChatColor.RED + " varar " + ChatColor.DARK_RED + data.banned + ChatColor.RED + ": " + ChatColor.DARK_RED + data.reason);
				
				return true;
			}
		});

		Sandkassinn.log.info("Ban module enabled.");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void eventLogin(PlayerJoinEvent e) {
		Player player = e.getPlayer();
		BanData data = ds.check(player.getName());
		if (data == null) {
			return;
		}

		// Might has ban
		switch(data.type) {
			case PARDON:
				// Unbanned, do nothing
				break;
			case PERMABAN:
				// Permabanned, frown upon user
				e.setJoinMessage(null);
				// TODO: i18n
				player.kickPlayer(ChatColor.DARK_RED + "Endanlegt bann : " + ChatColor.RED + data.reason);
				break;
			case TEMPBAN:
				if (data.date_expire < System.currentTimeMillis()/1000L) {
					// Ban expired, do nothing
					break;
				}
				// Temporary ban, frown upon user
				e.setJoinMessage(null);
				Date date = new Date((long)data.date_expire*1000);
				DateFormat fmt = new SimpleDateFormat("dd.MM.yyyy hh:mm");
				// TODO: i18n
				player.kickPlayer(ChatColor.DARK_RED + "T�mabundid bann til " + fmt.format(date) + " : " + ChatColor.RED + data.reason);
				break;
			// Warning, warn user
			case WARNING:
				// TODO: i18n
				player.sendMessage(ChatColor.DARK_RED + "Adv�run : " + ChatColor.RED + data.reason);
				break;
		}
	}

	/**
	 * Get reason from arguments
	 * @param args					Arguments passed to command
	 * @param start					Start from given arg index
	 * @return						String with given reason, or default reason
	 */
	private String getReason(String[] args, int start) {
		if (args.length > start) {
			StringBuilder reason = new StringBuilder();
			reason.append(args[start]);
			for (int i = start+1; i < args.length; ++i) {
				reason.append(" ").append(args[i]);
			}
			return reason.toString();
		} else {
			String reason = Sandkassinn.plugin.getConfig().getString("sandkassinn.modules.ban.default-reason");
			if (reason == null) {
				// TODO: i18n
				reason = "Engin �st�da";
			}
			return reason;
		}
	}

	private OfflinePlayer getPlayer(String playerName, CommandSender sender) {
		OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(playerName);
		if (!player.hasPlayedBefore()) {
			// TODO: i18n
			sender.sendMessage(ChatColor.DARK_RED + player.getName() + ChatColor.RED + " hefur aldrei komid inn � serverinn");
			return null;
		}
		return player;
	}

	private Long getTime(String time, CommandSender sender) {
		try {
			return getTime(time);
		} catch (Exception e) {
			// TODO: i18n
			HashMap<String, String> replacements = new HashMap<String, String>();
			String message = "&4'{time}' &cer �gilt t�ma format.";
			replacements.put("time", time);
			sender.sendMessage(this.buildString(message, replacements));
			return null;
		}
	}
	
	private String buildString(String text, HashMap<String, String> replacements) {
		Pattern pattern = Pattern.compile("\\{(.+?)\\}");
		Matcher matcher = pattern.matcher(text);
		StringBuilder builder = new StringBuilder();
		int i = 0;
		while (matcher.find()) {
		    String replacement = replacements.get(matcher.group(1));
		    builder.append(text.substring(i, matcher.start()));
		    if (replacement == null)
		        builder.append(matcher.group(0));
		    else
		        builder.append(replacement);
		    i = matcher.end();
		}
		builder.append(text.substring(i, text.length()));
		return builder.toString().replaceAll("&", new Character((char) 167).toString());
	}

	/**
	 * Convert something like 1Y2M3w4d5h6m7s to seconds
	 *
	 * @param time
	 * @return
	 * @throws Exception Invalid time format
	 */
	private long getTime(String time) throws Exception {
		long seconds = 0;
		StringBuilder stack = new StringBuilder();
		for(int i = 0; i < time.length(); ++i) {
			if(Character.isDigit(time.charAt(i))) {
				stack.append(time.charAt(i));
			} else {
				int s = Integer.parseInt(stack.toString());
				stack = new StringBuilder();

				// Seconds
				if(time.charAt(i) == 's') {
					seconds += s;
					continue;
				}

				// Minutes
				s = s * 60;
				if(time.charAt(i) == 'm') {
					seconds += s;
					continue;
				}

				// Hours
				s = s * 60;
				if(time.charAt(i) == 'h') {
					seconds += s;
					continue;
				}

				// Days
				s = s * 24;
				if(time.charAt(i) == 'd') {
					seconds += s;
					continue;
				}

				// Weeks
				s = s * 7;
				if(time.charAt(i) == 'w') {
					seconds += s;
					continue;
				}

				// Months
				s = s * 4;
				if(time.charAt(i) == 'M') {
					seconds += s;
					continue;
				}

				// Years
				s = s * 12;
				if(time.charAt(i) == 'Y') {
					seconds += s;
					continue;
				}

				throw new Exception("Invalid time format");
			}
		}
		if (seconds == 0) {
			throw new Exception("Invalid time format");
		}
		return seconds;
	}
}
