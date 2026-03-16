<script setup lang="ts">
import { useForm, Link } from '@inertiajs/vue3'

const form = useForm({
  title: '',
  description: '',
  date: '',
})

function submit() {
  form.post('/events')
}
</script>

<template>
  <div style="max-width: 600px; margin: 40px auto; font-family: sans-serif;">
    <Link href="/events" style="color: #6b7280;">← Back to Events</Link>

    <h1>Create Event</h1>

    <form @submit.prevent="submit" style="display: flex; flex-direction: column; gap: 16px;">
      <div>
        <label for="title" style="display: block; font-weight: bold; margin-bottom: 4px;">Title</label>
        <input id="title" v-model="form.title" type="text"
               style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px;" />
        <span v-if="form.errors.title" style="color: #dc2626; font-size: 14px;">
          {{ form.errors.title }}
        </span>
      </div>

      <div>
        <label for="description" style="display: block; font-weight: bold; margin-bottom: 4px;">Description</label>
        <textarea id="description" v-model="form.description" rows="3"
                  style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px;" />
        <span v-if="form.errors.description" style="color: #dc2626; font-size: 14px;">
          {{ form.errors.description }}
        </span>
      </div>

      <div>
        <label for="date" style="display: block; font-weight: bold; margin-bottom: 4px;">Date</label>
        <input id="date" v-model="form.date" type="date"
               style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px;" />
        <span v-if="form.errors.date" style="color: #dc2626; font-size: 14px;">
          {{ form.errors.date }}
        </span>
      </div>

      <button type="submit" :disabled="form.processing"
              style="padding: 10px 20px; background: #2563eb; color: white; border: none; border-radius: 4px; cursor: pointer;">
        Create Event
      </button>
    </form>
  </div>
</template>
