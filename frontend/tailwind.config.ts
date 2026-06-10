import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#111827',
        parchment: '#faf7ef',
        ember: '#ca3500',
        pine: '#12664f',
        mist: '#d8e2e9'
      },
      boxShadow: {
        card: '0 18px 46px -18px rgba(2, 6, 23, 0.7), 0 10px 24px -16px rgba(51, 65, 85, 0.55)'
      },
      fontFamily: {
        display: ['"Space Grotesk"', 'system-ui', 'sans-serif'],
      },
      keyframes: {
        'pulse-sweep': { '0%': { left: '-40%' }, '100%': { left: '140%' } },
        grow: { '0%': { width: '0%' }, '100%': { width: '74%' } },
      },
      animation: {
        'pulse-sweep': 'pulse-sweep 2.5s ease-in-out infinite',
        grow: 'grow 2s ease-out forwards',
      },
    },
  },
  plugins: [require('@tailwindcss/typography')],
};

export default config;
