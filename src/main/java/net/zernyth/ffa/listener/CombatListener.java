package net.zernyth.ffa.listener;

import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.combat.CombatTagService;
import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.projectile.EntityProjectile;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;

/**
 * Tags both players when one really hits the other in an arena. Runs last and only for hits that
 * went through, so a hit the safe zone or the respawn shield blocked starts no fight.
 */
public final class CombatListener implements Listener {
    private final ArenaSessionService arenas;
    private final CombatTagService combat;

    public CombatListener(ArenaSessionService arenas, CombatTagService combat) {
        this.arenas = arenas;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || !arenas.areInSameArena(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        combat.tag(attacker.getUniqueId(), victim.getUniqueId());
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof EntityProjectile projectile && projectile.shootingEntity instanceof Player player) {
            return player;
        }
        return null;
    }
}
