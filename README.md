# 🛡️ CMCLiteBans

**Advanced Punishment Management & Escalation Addon for LiteBans**

CMCLiteBans is a comprehensive administration tool designed to work alongside [LiteBans](https://www.spigotmc.org/resources/litebans.3715/). It provides a GUI-based punishment system, smart history escalation (automatic punishment leveling), alt-account detection, and screen-share (SS) management.

---

## 🔒 Ownership & Rights
**You cannot claim ownership, authorship, or branding of this project without explicit permission from the creator.**

While the **GNU GPLv3** license allows modification and redistribution, it does not allow you to present the project as your own or remove original credits.

## 📜 License – GNU GPLv3
This project is licensed under the **GNU General Public License v3.0**, which means:
- You may **use, modify, and redistribute** the project.
- Any redistributed or modified version **must remain under GPLv3**.
- You **must keep original credits** and license notices intact.
- You **must clearly document** any changes you make.

For full details, see the included `LICENSE` file.

---

## ⚙️ Features

### ⚖️ Smart Punishment System
- **Auto-Escalation:** The plugin calculates the punishment severity based on the player's history.
  - *Example:* 1st offense = Warn, 2nd = Mute 30m, 3rd = Ban.
- **Categorized Offenses:** Rules are split into categories (Chat, Gameplay, Cheating) with unique tracking IDs (e.g., `#101`, `#305`).

### 🕵️ Alt Account Detection
- **IP Scanning:** When selecting a player, the system automatically checks the database for other accounts sharing the same IP address.
- **Cross-Account Punishments:** The system can detect evasion attempts by checking history across IP addresses, not just UUIDs.

### 🖥️ GUI Administration
- **Visual Interface:** Admins use `/bausme` to open a user-friendly GUI to select players and reasons.
- **Head Display:** Shows target player info and the admin's own punishment statistics.

### 🚨 Screen Share (Patikra) System
- **Freeze & Notify:** Commands to jail/freeze a player for inspection (`/patikra`).
- **Timers:** Automated countdowns for players to join Discord.
- **Auto-Ban:** Automatically punishes players who log out during a screen share.

### 📝 Logging & Integrations
- **Discord Webhooks:** Sends detailed punishment logs (with admin name, reason, and duration) to a configured Discord channel.
- **Reset History:** Console/Admin command to "void" specific punishment types from a player's history without deleting the database record.
- **CoreProtect Rollback:** (Optional) Automatically rolls back player actions upon banning.

---

## 🛠 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/bausme` | `bausme.use` | Opens the main administration GUI. |
| `/bausme <player>` | `bausme.use` | Opens the GUI for a specific target. |
| `/bausme reload` | `bausme.admin` | Reloads the `config.yml` and database connection. |
| `/bausme nuimti <type> <player>` | `bausme.admin` | Resets punishment points (voids history) for a player. Types: `chat`, `cheat`, `all`. |
| `/patikra <player> <discord>` | `bausme.patikra` | Starts a screen share session/freeze on a player. |

### Other Permissions
- `bausme.admin`: Bypasses punishment limits and cooldowns.
- `bausme.cooldown.<rank>`: Sets specific cooldown timers for different staff ranks (configured in `config.yml`).

---

## 📦 Installation

1.  **Prerequisites:**
    *   Java 21 or higher.
    *   **LiteBans** (Required).
    *   *Optional:* AuthMe, CoreProtect.
2.  Download the latest `.jar` release.
3.  Place the file in your server's `plugins` folder.
4.  Restart the server.
5.  Configure `config.yml` (Database details are required if `use-custom-database` is true, otherwise it hooks into LiteBans API).

---

## 🤝 Contributing
Contributions are welcome!
- All pull requests must follow the project’s coding style.
- You must respect the **GPLv3 license**.
- Major changes or feature additions should be discussed with the author beforehand.

## 📬 Contact
*   **Discord:** `detective_0`
*   **Issues:** Please report bugs via the GitHub Issues tab.



🤝 Huge thanks to MineAcademy and Matej Pacan, who teaches practically all the programs and provides live support at scheduled times.
If you want to join the program and learn something new — or refresh your existing knowledge — I highly recommend joining the course.

https://app.mineacademy.org/join/project-orion?u=ieqq
