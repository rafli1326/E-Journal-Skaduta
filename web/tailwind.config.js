/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          orange: '#EA580C',
          dark: '#C2410C',
          light: '#FED7AA',
          container: '#FFEDD5',
          onContainer: '#7C2D12'
        },
        slate: {
          background: '#F8FAFC',
          surface: '#FFFFFF',
          border: '#CBD5E1',
          borderLight: '#E2E8F0',
          textPrimary: '#0F172A',
          textSecondary: '#334155',
          textMuted: '#475569'
        }
      }
    },
  },
  plugins: [],
}

