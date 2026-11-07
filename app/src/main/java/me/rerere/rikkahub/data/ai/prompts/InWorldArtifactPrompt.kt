package me.rerere.rikkahub.data.ai.prompts

val DEFAULT_IN_WORLD_ARTIFACTS_PROMPT = """
### In-World Visual Artifacts (Standard & 3D Interactive)
You have an In-World Visual Artifact renderer. Whenever a tangible physical object appears in the narrative or roleplay (such as a handwritten letter, note, diary page, phone screen, receipt, contract, menu, newspaper, wanted poster, or item card), render it inside an ```artifact ... ``` block.

CRITICAL DISTINCTION — SVG / GENERAL CODING vs. IN-WORLD ARTIFACTS:
- If the user asks you to draw an SVG, write code, create an illustration, or provide standard web/software snippets (HTML, JavaScript, Python, CSS, XML):
  * Use standard markdown code blocks with the exact language tag: ```svg ... ```, ```xml ... ```, or ```html ... ```.
  * Standard code blocks render as regular syntax-highlighted code.
  * Do NOT use ```artifact``` for regular code, SVG graphics, or developer requests.
- Use ```artifact ... ``` ONLY when depicting a real, physical in-world narrative prop inside the scene.

RULES FOR IN-WORLD VISUALS:
1. Tag: Always use ```artifact ... ```.
2. Content: Inside ```artifact```, output pure HTML and CSS only. Do not put markdown headers, markdown bold/italic, or commentary inside it.
3. Mobile Responsiveness & Sizing: The client displays the artifact at 100% full height with zero internal scrolling on mobile devices:
   * Always design for mobile responsiveness (`width: 100%; max-width: 360px; margin: 0 auto; box-sizing: border-box;`).
   * For letters, newspapers, and contracts: let them stretch comfortably (`width: 100%; box-sizing: border-box;`).
   * For phones, tickets, or receipts: center them (`max-width: 320px; margin: 0 auto;`).
   * Never wrap the object in an empty outer halo.
4. Material Styling: Set explicit background colors and text colors on the container matching the real object (aged parchment, dark glass screen, thermal paper, neon sign, etc.). Use borders, box-shadows, subtle rotation, cursive fonts, and gradients.
5. 3D Interactive Props (Optional Upgrade):
   * When fitting the scene, you can create tactile 3D objects using CSS 3D transforms (`perspective: 800px; transform-style: preserve-3d; transition: transform 0.6s;`).
   * Examples: Double-sided business or tarot cards that flip on tap/hover (`transform: rotateY(180deg)`), fold-out classified dossiers, or layered tactile badges.
   * Ensure touch targets are at least 44x44px and safe for mobile screens (`touch-action: pan-y; overflow: hidden;`).
6. Security: Zero `<script>`, zero `<iframe>`, zero external image URLs. Use pure CSS.

EXAMPLE:
```artifact
<div style="max-width:320px;margin:12px auto;padding:18px 22px;background:#fbfbf6;border-radius:4px;box-shadow:0 8px 24px rgba(0,0,0,0.35);transform:rotate(-1.5deg);color:#1a202c;font-family:cursive;">
  <div style="font-weight:bold;font-size:18px;border-bottom:1px solid #e2e8f0;padding-bottom:6px;margin-bottom:10px;">Confidential Note</div>
  <p style="margin:0;line-height:1.6;font-size:15px;">Meet me upstairs in Room 402. Don't knock.</p>
  <div style="margin-top:14px;text-align:right;font-weight:bold;color:#e53e3e;">— Velvet</div>
</div>
```
""".trimIndent()
