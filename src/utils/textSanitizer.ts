/**
 * Centralized text normalization/sanitization for VASU Voice Assistant.
 *
 * Use sanitizeForDisplay() for chat messages shown to the user.
 * Use sanitizeForTTS() for text sent to TTS engines.
 *
 * This ensures consistent text quality across all layers.
 */

// Unicode combining diacritical marks range (U+0300–U+036F)
// These are the marks that cause garbled text like p̥, ẏ, a͟
const COMBINING_MARKS_REGEX = /[\u0300-\u036F]/g;

// Zero-width and invisible control characters
const INVISIBLE_CHARS_REGEX = /[\u200B-\u200F\u2028-\u202F\u2060-\u206F\uFEFF\u00AD]/g;

// Multiple spaces collapse
const MULTI_SPACE_REGEX = / {2,}/g;

/**
 * Sanitize text for display in the chat UI.
 *
 * - NFC normalize (standard Unicode form)
 * - Strip combining diacritical marks (accents from AI models)
 * - Strip zero-width/invisible characters
 * - Preserve legitimate Hindi Devanagari (U+0900–U+097F)
 * - Preserve legitimate Roman/Hinglish text
 * - Collapse multiple spaces
 */
export function sanitizeForDisplay(text: string): string {
  if (!text) return '';

  let cleaned = text;

  // NFC normalize first (standard form)
  cleaned = cleaned.normalize('NFC');

  // Remove combining diacritical marks (U+0300–U+036F)
  // These cause garbled text like p̥, ẏ, a͟
  cleaned = cleaned.replace(COMBINING_MARKS_REGEX, '');

  // Remove zero-width and invisible control characters
  cleaned = cleaned.replace(INVISIBLE_CHARS_REGEX, '');

  // Collapse multiple spaces
  cleaned = cleaned.replace(MULTI_SPACE_REGEX, ' ');

  // Trim
  cleaned = cleaned.trim();

  return cleaned;
}

/**
 * Sanitize text for TTS input.
 *
 * - Applies display sanitization
 * - Does NOT apply toPhoneticHinglish (that's done in TTS engine if needed)
 * - Strips markdown formatting
 * - Strips prompt injection artifacts
 */
export function sanitizeForTTS(text: string): string {
  if (!text) return '';

  let cleaned = sanitizeForDisplay(text);

  // Strip markdown formatting
  cleaned = cleaned
    .replace(/```[\s\S]*?```/g, '')           // Code blocks
    .replace(/`([^`]+)`/g, '$1')              // Inline code
    .replace(/[*_~#]/g, '')                   // Bold/italic/headers
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1') // Links → text only
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '')   // Images → remove
    .replace(/^[-=]{3,}$/gm, '')              // Horizontal rules
    .replace(/^>\s*/gm, '');                  // Blockquotes

  // Strip prompt injection artifacts
  cleaned = cleaned
    .replace(/^:\s*STRICTLY\b.*$/gim, '')
    .replace(/^CRITICAL SCRIPT.*$/gim, '')
    .replace(/^MUST FOLLOW STRICTLY.*$/gim, '')
    .replace(/^:\s*STRICTLY/gi, '')
    .replace(/^:\s*/, '')
    .replace(/\{.*?\}/g, '')
    .replace(/\[.*?\]/g, '');

  // Collapse multiple spaces
  cleaned = cleaned.replace(MULTI_SPACE_REGEX, ' ');

  // Trim
  cleaned = cleaned.trim();

  return cleaned;
}
