module.exports = {
  apps: [
    {
      name: 'weblog-user',
      script: '.output/server/index.mjs',
      instances: 1,
      exec_mode: 'fork',
      autorestart: true,
      max_restarts: 10,
      min_uptime: '10s',
      max_memory_restart: '300M',
      watch: false,
      env: {
        NODE_ENV: 'production',
        PORT: 3000,
        HOST: '0.0.0.0'
      },
      error_file: '/var/log/weblog/user-error.log',
      out_file: '/var/log/weblog/user-out.log',
      merge_logs: true,
      log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
      // 优雅关闭
      kill_timeout: 5000,
      listen_timeout: 10000,
      shutdown_with_message: true
    }
  ]
}
