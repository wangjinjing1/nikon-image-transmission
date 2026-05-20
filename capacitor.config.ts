import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.nikon.transfer',
  appName: '尼康图传',
  webDir: 'dist',
  android: {
    allowMixedContent: true
  }
};

export default config;
