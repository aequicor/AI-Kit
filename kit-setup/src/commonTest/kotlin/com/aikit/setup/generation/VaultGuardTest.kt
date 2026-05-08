package com.aikit.setup.generation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the path-level invariant behind the `vault_write_refused` error code.
 * Anything under any `vault/` segment is user data — the generator must
 * refuse the write rather than silently overwrite it on `kit-setup generate`.
 *
 * A test failure here is a public-contract change: agents and the docs site
 * pattern-match on this code.
 */
class VaultGuardTest {

    @Test
    fun rejectsTopLevelVaultPath() {
        assertTrue(refersToVault("vault/specs/features/auth/login/spec.md"))
    }

    @Test
    fun rejectsNestedVaultSegment() {
        // Reason: future templates may emit under a nested target, but the
        // guard is segment-based — protection survives unexpected layouts.
        assertTrue(refersToVault(".claude/vault/cache.md"))
    }

    @Test
    fun rejectsBareVaultDirectory() {
        assertTrue(refersToVault("vault"))
    }

    @Test
    fun toleratesWindowsBackslashSeparators() {
        // Reason: a buggy template that emits "vault\specs\x.md" on Windows
        // must still be caught. Forward-slash is the convention; the guard
        // also handles backslash to defend against regressions.
        assertTrue(refersToVault("vault\\specs\\foo.md"))
    }

    @Test
    fun allowsLookalikePrefixes() {
        // Reason: only the literal segment `vault` is reserved. Adapter
        // paths like `myvault.txt` or `vaulted-config.md` are user-facing
        // configuration files, not user data, and must pass through.
        assertFalse(refersToVault("myvault/data.md"))
        assertFalse(refersToVault("vaulted-config.md"))
        assertFalse(refersToVault("config-vault.json"))
    }

    @Test
    fun allowsCaseDifferentDirectoryName() {
        // Reason: convention is lowercase `vault`. A user with `Vault/` is
        // outside the convention — we don't claim that path. Documented
        // behaviour, not a hidden quirk.
        assertFalse(refersToVault("Vault/specs/x.md"))
        assertFalse(refersToVault("VAULT/specs/x.md"))
    }

    @Test
    fun allowsTypicalAdapterPaths() {
        // Reason: regression check — none of the four supported adapters'
        // output trees should accidentally be flagged.
        assertFalse(refersToVault(".claude/agents/Architect.md"))
        assertFalse(refersToVault(".cursor/rules/conventions.mdc"))
        assertFalse(refersToVault(".opencode/agents/Main.md"))
        assertFalse(refersToVault(".aider.conf.yml"))
        assertFalse(refersToVault("CLAUDE.md"))
        assertFalse(refersToVault("AGENTS.md"))
    }
}
