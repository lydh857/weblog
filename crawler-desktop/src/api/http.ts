import axios from 'axios'

export const workerApi = axios.create({
  baseURL: 'http://127.0.0.1:17891',
  timeout: 15000
})
