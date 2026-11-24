import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { resolveContactEmail } from './src/api/config.ts'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // The privacy policy must name a contact, so a production build without one fails here
  // instead of shipping a policy page with no way to reach the operator.
  if (mode === 'prod' && !resolveContactEmail(loadEnv(mode, process.cwd(), 'VITE_').VITE_CONTACT_EMAIL)) {
    throw new Error('운영 빌드에는 VITE_CONTACT_EMAIL(개인정보 문의처)이 필요합니다.')
  }
  return {
    plugins: [react()],
    // HEIC decoder is loaded lazily inside a module worker.
    worker: { format: 'es' },
  }
})
