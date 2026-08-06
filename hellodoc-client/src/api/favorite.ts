import request from '../utils/request'

export const toggleFavorite = (docId: number) => request.post(`/api/docs/${docId}/favorite`)
export const removeFavorite = (docId: number) => request.delete(`/api/docs/${docId}/favorite`)
export const checkIsFavorite = (docId: number) => request.get(`/api/docs/${docId}/favorite`)
export const getMyFavorites = () => request.get('/api/docs/favorites')
