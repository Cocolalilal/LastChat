# Skills and additional context

Skills are available capabilities. Character settings control which skills a character can access,
and whether it can choose skills automatically. Access alone never loads a skill's instructions.
The `manage_skills` tool exposes a short name and description for each eligible skill; invocation
loads its instructions for the current response, including subsequent tool steps. The following
user message starts with no skills active.

Type `/` in either the Android or web composer to open the skill picker, or open Skills from the
composer's add-ons menu. Selecting a skill inserts `/skillname `; add the task and send the message.
Only a leading, exact command invokes a skill. Manual commands remain available when automatic
selection is disabled. Per-skill manual-only and slash-command options are respected.

**Additional context**, between Skills and Lorebooks on the add-ons page, contains always-on prompt
snippets. Create a name and prompt, then assign individual characters or all characters. Snippets
can be edited, disabled, or deleted independently of skill access and lorebook activation.
Assignments are also available in each character's Skills page. Backups have a separate Additional
context option.

Existing always-enabled skills move into Additional context automatically, preserving their IDs,
character scope, attachments, and placement. They are removed from the skill list to avoid duplicate
injection. Ordinary skills, their portable packages, and import/export support remain available.
Legacy per-character and per-chat default skill selections no longer activate instructions.

## ARMv7 rootfs verification

The installer skips `.` and `./` directory entries (including GNU/PAX name overrides) while consuming
the entry payload and tar padding. Child paths still undergo traversal validation.

The optional smoke test extracts the actual Alpine ARMv7 archive with `RootfsInstaller`, validates
the ARM ELF binary, and executes its dynamically linked BusyBox shell under QEMU:

```bash
LASTCHAT_ARMHF_ARCHIVE=/absolute/path/alpine-minirootfs-3.19.9-armv7.tar.gz \
LASTCHAT_QEMU_ARM=/absolute/path/qemu-arm-static \
./gradlew :workspace:testDebugUnitTest --tests '*RootfsArmhfSmokeTest'
```

Supply a trusted local archive and QEMU executable. The test performs no downloads. This checks the
rootfs extraction and ARMHF binary/loader, while PRoot behavior on an ARMv7 phone requires a device.
