import { visit } from 'unist-util-visit';
import type { Node, Parent } from 'unist';
import type { RpStyleRule } from '~/types';

const STANDARD_PATTERNS = new Set(["*", "**", "~~", "`", "#", "##", "###", "####", "#####", "######", ">"]);

export default function remarkRp(rules?: RpStyleRule[]) {
  return (tree: Node) => {
    if (!rules || rules.length === 0) return;

    // Filter out standard ones and disabled ones
    const customRules = rules.filter(r => r.enabled && !STANDARD_PATTERNS.has(r.pattern));
    if (customRules.length === 0) return;

    visit(tree, 'text', (node: any, index: number, parent: Parent) => {
      let currentString = node.value;
      if (!currentString) return;

      type Match = { start: number; end: number; content: string; color: string; pattern: string };
      const matches: Match[] = [];

      for (const rule of customRules) {
        // Escape pattern for regex
        const escaped = rule.pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const regex = new RegExp(`${escaped}(.+?)${escaped}`, 'g');
        let m;
        while ((m = regex.exec(currentString)) !== null) {
          matches.push({
            start: m.index,
            end: m.index + m[0].length,
            content: m[1],
            color: rule.colorHex,
            pattern: rule.pattern
          });
        }
      }

      if (matches.length === 0) return;

      // Sort by start index
      matches.sort((a, b) => a.start - b.start);
      
      // Remove overlaps
      const nonOverlapping: Match[] = [];
      let lastEnd = -1;
      for (const m of matches) {
        if (m.start >= lastEnd) {
          nonOverlapping.push(m);
          lastEnd = m.end;
        }
      }

      if (nonOverlapping.length === 0) return;

      // Now create new nodes replacing this node
      const newNodes: Node[] = [];
      let currentIndex = 0;
      for (const m of nonOverlapping) {
        if (m.start > currentIndex) {
          newNodes.push({
            type: 'text',
            value: currentString.substring(currentIndex, m.start)
          });
        }
        
        // Push HTML node wrapping the content
        // This will be parsed by rehype-raw
        newNodes.push({
          type: 'html',
          value: `<span style="color: ${m.color}">${m.content}</span>`
        });

        currentIndex = m.end;
      }

      if (currentIndex < currentString.length) {
        newNodes.push({
          type: 'text',
          value: currentString.substring(currentIndex)
        });
      }

      // Replace the current node with newNodes in parent
      parent.children.splice(index, 1, ...newNodes);
      
      // Return the new index to visit to avoid visiting the newly added text nodes infinitely
      return index + newNodes.length;
    });
  };
}
