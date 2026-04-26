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
        card: '0 16px 40px -20px rgba(17, 24, 39, 0.35)'
      }
    },
  },
  plugins: [],
};

export default config;
