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
      }
    },
  },
  plugins: [require('@tailwindcss/typography')],
};

export default config;
