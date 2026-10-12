/* Tailwind (CDN) theme: shared by every page. Load right after the Tailwind CDN script.
 * Skill2Job runs a single dark, neon-purple theme (see app.css :root for the matching CSS
 * variables used outside Tailwind utility classes, e.g. in app.css itself). Overriding the
 * default "slate" scale here means every existing `text-slate-500`, `bg-slate-100`,
 * `border-slate-200`, etc. across every page automatically becomes part of the dark theme,
 * without having to hunt down and rewrite each usage by hand. */
if (typeof tailwind !== 'undefined') tailwind.config = {
  theme: {
    extend: {
      colors: {
        // Primary heading/emphasis text color. Used as `text-ink` ~80 times across the app;
        // was a dark ink color on a white background, is now a bright near-white on a dark one.
        ink: '#f4eefc',
        // A rich, solid elevated-surface purple (distinct from the page background) used where
        // a literal fill is needed instead of text, e.g. a toggle's "checked" state.
        deep: '#2a1854',
        // A card/nav/footer surface color, a step lighter than the page background.
        panel: '#110821',
        brand: { DEFAULT: '#8b2cf5', dark: '#e9d5ff', light: 'rgba(168,85,247,.16)', pink: '#d12bd9' },
        miss: '#ff6b81',
        weak: '#ffc266',
        strong: '#3ddc97',
        slate: {
          25: '#0c0416',
          50: '#110821',
          100: '#170c2c',
          200: '#2c1d47',
          300: '#3d2d5c',
          400: '#8d7fab',
          500: '#ab9fc7',
          600: '#bcb1d6',
          700: '#d4cbe8',
          800: '#eee8f8',
          900: '#f7f4fc',
          950: '#fffdff'
        }
      },
      fontFamily: {
        display: ['"Space Grotesk"', '"Hind Siliguri"', 'system-ui', 'sans-serif'],
        sans: ['"Space Grotesk"', '"Hind Siliguri"', 'system-ui', 'sans-serif']
      }
    }
  }
};
