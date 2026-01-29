<script setup>
import { ref, onMounted } from 'vue'

const API_BASE = 'http://localhost:8080/api/orders'

const sourceRecords = ref([])
const indexRecords = ref([])
const newOrder = ref({ customerId: '', totalAmount: '', status: 'CREATED' })

const fetchSource = async () => {
  try {
    const res = await fetch(`${API_BASE}/source`)
    sourceRecords.value = await res.json()
  } catch (err) {
    console.error('Failed to fetch source:', err)
  }
}

const fetchIndex = async () => {
  try {
    const res = await fetch(`${API_BASE}/index`)
    indexRecords.value = await res.json()
  } catch (err) {
    console.error('Failed to fetch index:', err)
  }
}

const fetchAll = () => {
  fetchSource()
  fetchIndex()
}

const createOrder = async () => {
  if (!newOrder.value.customerId || !newOrder.value.totalAmount) return
  
  try {
    await fetch(API_BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newOrder.value)
    })
    newOrder.value = { customerId: '', totalAmount: '', status: 'CREATED' }
    fetchSource()
    // Give Kafka/ES a slight delay to process
    setTimeout(fetchIndex, 500)
  } catch (err) {
    console.error(err)
  }
}

const updateStatus = async (id, currentStatus) => {
  const nextStatus = currentStatus === 'CREATED' ? 'PENDING' : 'SHIPPED'
  try {
    await fetch(`${API_BASE}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: nextStatus })
    })
    fetchSource()
    setTimeout(fetchIndex, 500)
  } catch (err) {
    console.error(err)
  }
}

const deleteOrder = async (id) => {
  try {
    await fetch(`${API_BASE}/${id}`, { method: 'DELETE' })
    fetchSource()
    setTimeout(fetchIndex, 500)
  } catch (err) {
    console.error(err)
  }
}

const getStatusClass = (status) => {
  if (!status) return ''
  return status.toLowerCase()
}

onMounted(() => {
  fetchAll()
})
</script>

<template>
  <div class="app-container">
    <header class="header">
      <h1>CDC Sync Engine</h1>
      <p>Real-time data synchronization via Debezium and Kafka</p>
    </header>

    <div class="dashboard-grid">
      <!-- Left Pane: Postgres Source -->
      <div class="glass-panel">
        <div class="panel-header">
          <h2>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"></ellipse><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path></svg>
            PostgreSQL <span class="badge">Source</span>
          </h2>
          <button @click="fetchSource" style="background: rgba(255,255,255,0.1)">Refresh</button>
        </div>

        <form @submit.prevent="createOrder" class="create-form">
          <div style="display: flex; gap: 12px; align-items: flex-end;">
            <div class="form-group" style="margin: 0; flex: 1;">
              <label>Customer ID</label>
              <input v-model="newOrder.customerId" required placeholder="CUST-001" type="text" />
            </div>
            <div class="form-group" style="margin: 0; flex: 1;">
              <label>Amount ($)</label>
              <input v-model="newOrder.totalAmount" required placeholder="99.99" type="number" step="0.01" />
            </div>
            <button type="submit" style="height: 38px;">Insert Row</button>
          </div>
        </form>

        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Customer</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in sourceRecords" :key="order.id">
              <td>{{ order.id }}</td>
              <td>{{ order.customerId }}</td>
              <td>${{ order.totalAmount.toFixed(2) }}</td>
              <td>
                <div class="status-indicator">
                  <span class="status-dot" :class="getStatusClass(order.status)"></span>
                  {{ order.status }}
                </div>
              </td>
              <td>
                <div class="action-bar">
                  <button @click="updateStatus(order.id, order.status)" style="padding: 4px 8px; font-size: 0.75rem;">Advance</button>
                  <button class="danger" @click="deleteOrder(order.id)" style="padding: 4px 8px; font-size: 0.75rem;">Delete</button>
                </div>
              </td>
            </tr>
            <tr v-if="sourceRecords.length === 0">
              <td colspan="5" style="text-align: center; color: var(--color-text-muted);">No records found in Postgres</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Right Pane: Elasticsearch Index -->
      <div class="glass-panel">
        <div class="panel-header">
          <h2>
             <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
            Elasticsearch <span class="badge" style="background: rgba(16, 185, 129, 0.1); color: #34d399;">Read Model</span>
          </h2>
          <button @click="fetchIndex" style="background: rgba(255,255,255,0.1)">Refresh</button>
        </div>
        
        <table class="data-table">
          <thead>
            <tr>
              <th>Doc ID</th>
              <th>Customer</th>
              <th>Amount</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="doc in indexRecords" :key="doc.id">
              <td>{{ doc.id }}</td>
              <td>{{ doc.customerId }}</td>
              <td>${{ doc.totalAmount.toFixed(2) }}</td>
              <td>
                <div class="status-indicator">
                  <span class="status-dot" :class="getStatusClass(doc.status)"></span>
                  {{ doc.status }}
                </div>
              </td>
            </tr>
            <tr v-if="indexRecords.length === 0">
              <td colspan="4" style="text-align: center; color: var(--color-text-muted);">No documents synced yet</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
